package dev.kanakku.ledger.repository;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static dev.kanakku.jooq.Tables.MEMBER_BALANCES;

import dev.kanakku.jooq.tables.GroupMembers;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.HistoryEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** All SQL for the journal and the running balances. Journal tables are insert-only. */
@Repository
public class JournalRepository {

  private final DSLContext dsl;

  JournalRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** An entry as the write path needs it: its kind and, for a keyed entry, its request hash. */
  public record StoredEntry(UUID id, String kind, byte[] requestHash) {}

  /**
   * Inserts the entry. A keyed entry whose key the group already used is skipped (a concurrent
   * duplicate waits on the unique index until the first commits or rolls back).
   *
   * @return false when the key was already taken and nothing was inserted
   */
  public boolean insertEntry(
      UUID id,
      UUID groupId,
      String kind,
      UUID actorUserId,
      UUID reversesEntryId,
      UUID replacesEntryId,
      String idempotencyKey,
      byte[] requestHash) {
    return dsl.insertInto(JOURNAL_ENTRIES)
            .set(JOURNAL_ENTRIES.ID, id)
            .set(JOURNAL_ENTRIES.GROUP_ID, groupId)
            .set(JOURNAL_ENTRIES.KIND, kind)
            .set(JOURNAL_ENTRIES.ACTOR_USER_ID, actorUserId)
            .set(JOURNAL_ENTRIES.REVERSES_ENTRY_ID, reversesEntryId)
            .set(JOURNAL_ENTRIES.REPLACES_ENTRY_ID, replacesEntryId)
            .set(JOURNAL_ENTRIES.IDEMPOTENCY_KEY, idempotencyKey)
            .set(JOURNAL_ENTRIES.REQUEST_HASH, requestHash)
            .onConflict(JOURNAL_ENTRIES.GROUP_ID, JOURNAL_ENTRIES.IDEMPOTENCY_KEY)
            .where(JOURNAL_ENTRIES.IDEMPOTENCY_KEY.isNotNull())
            .doNothing()
            .execute()
        == 1;
  }

  /**
   * Whether the user is a current member of the group, holding a share lock on their member row
   * until the transaction ends, so a concurrent leave waits for the post to commit (and a post that
   * meets an in-flight leave waits, then sees it). FOR SHARE, not FOR KEY SHARE: a leave only
   * updates left_at, a non-key column, so its FOR NO KEY UPDATE lock would pass a key-share lock.
   *
   * <p>Lock order on the write path: this member row, then the group's advisory lock, then balance
   * rows. Write-offs and merges also take member rows before the advisory lock; the nightly repair
   * takes only the advisory lock and balance rows, never a member row, so no cycle can form.
   */
  public boolean isCurrentMember(UUID groupId, UUID userId) {
    return dsl.select(GROUP_MEMBERS.ID)
        .from(GROUP_MEMBERS)
        .where(GROUP_MEMBERS.GROUP_ID.eq(groupId))
        .and(GROUP_MEMBERS.USER_ID.eq(userId))
        .and(GROUP_MEMBERS.LEFT_AT.isNull())
        .forShare()
        .fetchOptional()
        .isPresent();
  }

  /** The entry the group posted under the key, if the caller is a current member of the group. */
  public Optional<StoredEntry> findByKey(UUID groupId, String idempotencyKey, UUID callerId) {
    return dsl.select(JOURNAL_ENTRIES.ID, JOURNAL_ENTRIES.KIND, JOURNAL_ENTRIES.REQUEST_HASH)
        .from(JOURNAL_ENTRIES)
        .where(JOURNAL_ENTRIES.GROUP_ID.eq(groupId))
        .and(JOURNAL_ENTRIES.IDEMPOTENCY_KEY.eq(idempotencyKey))
        .and(callerIsMember(groupId, callerId))
        .fetchOptional(r -> new StoredEntry(r.value1(), r.value2(), r.value3()));
  }

  /** The entry if it belongs to the group and the caller is a current member of it. */
  public Optional<StoredEntry> findInGroup(UUID groupId, UUID entryId, UUID callerId) {
    return dsl.select(JOURNAL_ENTRIES.ID, JOURNAL_ENTRIES.KIND, JOURNAL_ENTRIES.REQUEST_HASH)
        .from(JOURNAL_ENTRIES)
        .where(JOURNAL_ENTRIES.GROUP_ID.eq(groupId))
        .and(JOURNAL_ENTRIES.ID.eq(entryId))
        .and(callerIsMember(groupId, callerId))
        .fetchOptional(r -> new StoredEntry(r.value1(), r.value2(), r.value3()));
  }

  /** The entry's lines in line order, if the caller is a current member of its group. */
  public List<JournalLine> linesOf(UUID groupId, UUID entryId, UUID callerId) {
    return dsl.select(JOURNAL_LINES.MEMBER_ID, JOURNAL_LINES.CURRENCY, JOURNAL_LINES.AMOUNT_MINOR)
        .from(JOURNAL_LINES)
        .where(JOURNAL_LINES.ENTRY_ID.eq(entryId))
        .and(JOURNAL_LINES.GROUP_ID.eq(groupId))
        .and(callerIsMember(groupId, callerId))
        .orderBy(JOURNAL_LINES.LINE_NO)
        .fetch(r -> new JournalLine(r.value1(), r.value2(), r.value3()));
  }

  /** Whether some entry already reverses this one. */
  public boolean hasReversal(UUID entryId) {
    return dsl.fetchExists(JOURNAL_ENTRIES, JOURNAL_ENTRIES.REVERSES_ENTRY_ID.eq(entryId));
  }

  /**
   * Takes the group's ledger lock in shared mode until the transaction ends. Every post takes it
   * first, before any balance row lock, so posts run in parallel but never alongside a repair.
   */
  public void lockGroupShared(UUID groupId) {
    dsl.fetch("select pg_advisory_xact_lock_shared(hashtextextended(?, 0))", lockName(groupId));
  }

  /** Takes the group's ledger lock exclusively until the transaction ends: no post can run. */
  public void lockGroupExclusive(UUID groupId) {
    dsl.fetch("select pg_advisory_xact_lock(hashtextextended(?, 0))", lockName(groupId));
  }

  private static String lockName(UUID groupId) {
    return "ledger:" + groupId;
  }

  public void insertLines(UUID entryId, UUID groupId, List<JournalLine> lines) {
    int lineNo = 0;
    for (JournalLine line : lines) {
      dsl.insertInto(JOURNAL_LINES)
          .set(JOURNAL_LINES.ENTRY_ID, entryId)
          .set(JOURNAL_LINES.LINE_NO, lineNo++)
          .set(JOURNAL_LINES.GROUP_ID, groupId)
          .set(JOURNAL_LINES.MEMBER_ID, line.memberId())
          .set(JOURNAL_LINES.CURRENCY, line.currency())
          .set(JOURNAL_LINES.AMOUNT_MINOR, line.amountMinor())
          .execute();
    }
  }

  /** Adds {@code delta} to the member's balance in the currency, creating the row if needed. */
  public void addToBalance(UUID groupId, UUID memberId, String currency, long delta) {
    dsl.insertInto(MEMBER_BALANCES)
        .set(MEMBER_BALANCES.GROUP_ID, groupId)
        .set(MEMBER_BALANCES.MEMBER_ID, memberId)
        .set(MEMBER_BALANCES.CURRENCY, currency)
        .set(MEMBER_BALANCES.BALANCE_MINOR, delta)
        .onConflict(MEMBER_BALANCES.GROUP_ID, MEMBER_BALANCES.MEMBER_ID, MEMBER_BALANCES.CURRENCY)
        .doUpdate()
        .set(MEMBER_BALANCES.BALANCE_MINOR, MEMBER_BALANCES.BALANCE_MINOR.plus(delta))
        .execute();
  }

  /** The member's balances if the caller is a current member of the group; empty otherwise. */
  public List<MoneyBalance> balancesOfMember(UUID groupId, UUID memberId, UUID callerId) {
    return dsl.select(MEMBER_BALANCES.CURRENCY, MEMBER_BALANCES.BALANCE_MINOR)
        .from(MEMBER_BALANCES)
        .where(MEMBER_BALANCES.GROUP_ID.eq(groupId))
        .and(MEMBER_BALANCES.MEMBER_ID.eq(memberId))
        .and(callerIsMember(groupId, callerId))
        .orderBy(MEMBER_BALANCES.CURRENCY)
        .fetch(r -> new MoneyBalance(r.value1(), r.value2()));
  }

  /** Every balance in the group if the caller is a current member of it; empty otherwise. */
  public List<MemberBalance> balancesOfGroup(UUID groupId, UUID callerId) {
    return dsl.select(
            MEMBER_BALANCES.MEMBER_ID, MEMBER_BALANCES.CURRENCY, MEMBER_BALANCES.BALANCE_MINOR)
        .from(MEMBER_BALANCES)
        .where(MEMBER_BALANCES.GROUP_ID.eq(groupId))
        .and(callerIsMember(groupId, callerId))
        .orderBy(MEMBER_BALANCES.MEMBER_ID, MEMBER_BALANCES.CURRENCY)
        .fetch(r -> new MemberBalance(r.value1(), r.value2(), r.value3()));
  }

  /**
   * A page of the group's entries in seq order with their lines, if the caller is a current member
   * of the group; empty otherwise.
   */
  public List<HistoryEntry> history(UUID groupId, UUID callerId, Long afterSeq, int limit) {
    var entries =
        dsl.select(
                JOURNAL_ENTRIES.ID,
                JOURNAL_ENTRIES.SEQ,
                JOURNAL_ENTRIES.KIND,
                JOURNAL_ENTRIES.ACTOR_USER_ID,
                JOURNAL_ENTRIES.CREATED_AT,
                JOURNAL_ENTRIES.REVERSES_ENTRY_ID,
                JOURNAL_ENTRIES.REPLACES_ENTRY_ID)
            .from(JOURNAL_ENTRIES)
            .where(JOURNAL_ENTRIES.GROUP_ID.eq(groupId))
            .and(callerIsMember(groupId, callerId))
            .and(afterSeq == null ? DSL.noCondition() : JOURNAL_ENTRIES.SEQ.gt(afterSeq))
            .orderBy(JOURNAL_ENTRIES.SEQ)
            .limit(limit)
            .fetch();
    if (entries.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = entries.map(r -> r.value1());
    Map<UUID, List<JournalLine>> lines = new HashMap<>();
    dsl.select(
            JOURNAL_LINES.ENTRY_ID,
            JOURNAL_LINES.MEMBER_ID,
            JOURNAL_LINES.CURRENCY,
            JOURNAL_LINES.AMOUNT_MINOR)
        .from(JOURNAL_LINES)
        .where(JOURNAL_LINES.ENTRY_ID.in(ids))
        .and(JOURNAL_LINES.GROUP_ID.eq(groupId))
        .and(callerIsMember(groupId, callerId))
        .orderBy(JOURNAL_LINES.ENTRY_ID, JOURNAL_LINES.LINE_NO)
        .forEach(
            r ->
                lines
                    .computeIfAbsent(r.value1(), k -> new ArrayList<>())
                    .add(new JournalLine(r.value2(), r.value3(), r.value4())));
    return entries.map(
        r ->
            new HistoryEntry(
                r.value1(),
                r.value2(),
                EntryKind.valueOf(r.value3()),
                r.value4(),
                r.value5(),
                r.value6(),
                r.value7(),
                List.copyOf(lines.getOrDefault(r.value1(), List.of()))));
  }

  /**
   * Locks one member's balance rows until the transaction ends and returns them. Write path only:
   * the caller is a write-off whose Owner check ran in the same transaction.
   */
  public List<MoneyBalance> lockBalancesOfMember(UUID groupId, UUID memberId) {
    return dsl.select(MEMBER_BALANCES.CURRENCY, MEMBER_BALANCES.BALANCE_MINOR)
        .from(MEMBER_BALANCES)
        .where(MEMBER_BALANCES.GROUP_ID.eq(groupId))
        .and(MEMBER_BALANCES.MEMBER_ID.eq(memberId))
        .orderBy(MEMBER_BALANCES.CURRENCY)
        .forUpdate()
        .fetch(r -> new MoneyBalance(r.value1(), r.value2()));
  }

  /**
   * A member balance that differs from the sum of its journal lines.
   *
   * @param journalMinor the sum of the member's lines in the currency (0 when there are none)
   * @param storedMinor the stored balance, or null when there is no row
   */
  public record Mismatch(UUID memberId, String currency, long journalMinor, Long storedMinor) {}

  /**
   * Groups where some stored balance differs from its journal sum. One statement, so it sees a
   * consistent snapshot: a post commits its lines and its balances together. System job only; it
   * returns nothing to any client.
   */
  public List<UUID> mismatchedGroups() {
    return dsl.fetch(
            """
            with j as (
                select group_id, member_id, currency, sum(amount_minor) as total
                from journal_lines
                group by group_id, member_id, currency
            )
            select distinct coalesce(j.group_id, b.group_id) as group_id
            from j
            full outer join member_balances b
                on b.group_id = j.group_id and b.member_id = j.member_id
                    and b.currency = j.currency
            where coalesce(j.total, 0) <> coalesce(b.balance_minor, 0)
            """)
        .getValues("group_id", UUID.class);
  }

  /**
   * The group's balances that differ from their journal sums, by currency then member. Read it
   * under {@link #lockGroupExclusive} to see every committed post.
   */
  public List<Mismatch> mismatchesOf(UUID groupId) {
    return dsl.fetch(
            """
            with j as (
                select member_id, currency, sum(amount_minor) as total
                from journal_lines
                where group_id = ?
                group by member_id, currency
            ), b as (
                select member_id, currency, balance_minor
                from member_balances
                where group_id = ?
            )
            select coalesce(j.member_id, b.member_id) as member_id,
                   coalesce(j.currency, b.currency) as currency,
                   coalesce(j.total, 0)::bigint as journal_minor,
                   b.balance_minor as stored_minor
            from j
            full outer join b on b.member_id = j.member_id and b.currency = j.currency
            where coalesce(j.total, 0) <> coalesce(b.balance_minor, 0)
            order by 2, 1
            """,
            groupId,
            groupId)
        .map(
            r ->
                new Mismatch(
                    r.get("member_id", UUID.class),
                    r.get("currency", String.class),
                    r.get("journal_minor", Long.class),
                    r.get("stored_minor", Long.class)));
  }

  /**
   * Overwrites only the given balance rows with their journal sums. A balance with no lines becomes
   * 0; no row is deleted.
   */
  public void repairBalances(UUID groupId, List<Mismatch> rows) {
    for (Mismatch row : rows) {
      dsl.insertInto(MEMBER_BALANCES)
          .set(MEMBER_BALANCES.GROUP_ID, groupId)
          .set(MEMBER_BALANCES.MEMBER_ID, row.memberId())
          .set(MEMBER_BALANCES.CURRENCY, row.currency())
          .set(MEMBER_BALANCES.BALANCE_MINOR, row.journalMinor())
          .onConflict(MEMBER_BALANCES.GROUP_ID, MEMBER_BALANCES.MEMBER_ID, MEMBER_BALANCES.CURRENCY)
          .doUpdate()
          .set(MEMBER_BALANCES.BALANCE_MINOR, DSL.excluded(MEMBER_BALANCES.BALANCE_MINOR))
          .execute();
    }
  }

  private static Condition callerIsMember(UUID groupId, UUID callerId) {
    GroupMembers me = GROUP_MEMBERS.as("me");
    return DSL.exists(
        DSL.selectOne()
            .from(me)
            .where(me.GROUP_ID.eq(groupId))
            .and(me.USER_ID.eq(callerId))
            .and(me.LEFT_AT.isNull()));
  }
}
