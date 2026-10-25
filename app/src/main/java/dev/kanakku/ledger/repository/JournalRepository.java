package dev.kanakku.ledger.repository;

import static dev.kanakku.jooq.Tables.GROUP_MEMBERS;
import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static dev.kanakku.jooq.Tables.MEMBER_BALANCES;

import dev.kanakku.jooq.tables.GroupMembers;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import java.util.List;
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

  public void insertEntry(UUID id, UUID groupId, String kind, UUID actorUserId) {
    dsl.insertInto(JOURNAL_ENTRIES)
        .set(JOURNAL_ENTRIES.ID, id)
        .set(JOURNAL_ENTRIES.GROUP_ID, groupId)
        .set(JOURNAL_ENTRIES.KIND, kind)
        .set(JOURNAL_ENTRIES.ACTOR_USER_ID, actorUserId)
        .execute();
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
