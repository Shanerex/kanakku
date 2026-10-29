package dev.kanakku.ledger.service;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.History;
import dev.kanakku.ledger.api.HistoryEntry;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.LedgerConflict;
import dev.kanakku.ledger.api.LedgerConflict.Reason;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.api.Posted;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.ledger.repository.JournalRepository.StoredEntry;
import dev.kanakku.platform.idempotency.IdempotencyService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Posts journal entries and reads balances. A post and its balance updates share one transaction,
 * and each group's balances stay at zero per currency because every entry sums to zero.
 */
@Service
class JournalService implements Journal, Balances, History {

  /** Upper-case ISO 4217 codes with a minor unit, as {@code platform.validation.ValidCurrency}. */
  private static final Set<String> CURRENCIES =
      Currency.getAvailableCurrencies().stream()
          .filter(c -> c.getDefaultFractionDigits() >= 0)
          .map(Currency::getCurrencyCode)
          .collect(Collectors.toUnmodifiableSet());

  /** Kinds a member posts; they need a key and are the only ones that can be reversed. */
  private static final Set<EntryKind> KEYED = EnumSet.of(EntryKind.EXPENSE, EntryKind.SETTLEMENT);

  /** Longest idempotency key a caller may pass; {@code replace} adds a short suffix to it. */
  static final int MAX_KEY_LENGTH = 255;

  private final JournalRepository journal;
  private final TransactionTemplate transactions;

  JournalService(JournalRepository journal, TransactionTemplate transactions) {
    this.journal = journal;
    this.transactions = transactions;
  }

  @Override
  public UUID post(JournalEntry entry) {
    requireBalanced(entry.lines());
    if (KEYED.contains(entry.kind()) || entry.kind() == EntryKind.REVERSAL) {
      throw new IllegalArgumentException(entry.kind() + " entries are posted with a key");
    }
    return transactions.execute(status -> postInTransaction(entry, null, null, null, null));
  }

  @Override
  public Posted post(JournalEntry entry, String idempotencyKey) {
    requireBalanced(entry.lines());
    requireKey(idempotencyKey);
    requireActor(entry.actorUserId());
    if (entry.kind() == EntryKind.REVERSAL) {
      throw new IllegalArgumentException("a reversal is posted with reverse()");
    }
    if (!KEYED.contains(entry.kind())) {
      throw new IllegalArgumentException(entry.kind() + " entries are posted without a key");
    }
    return transactions.execute(
        status -> {
          requireMember(entry.groupId(), entry.actorUserId());
          requireCurrentLineMembers(entry.groupId(), entry.lines());
          return postKeyed(entry, null, null, idempotencyKey, () -> {});
        });
  }

  @Override
  public Posted reverse(UUID groupId, UUID entryId, UUID actorUserId, String idempotencyKey) {
    requireKey(idempotencyKey);
    requireActor(actorUserId);
    return transactions.execute(
        status -> {
          requireMember(groupId, actorUserId);
          return reverseInTransaction(groupId, entryId, actorUserId, idempotencyKey);
        });
  }

  @Override
  public Posted replace(UUID groupId, UUID entryId, JournalEntry next, String idempotencyKey) {
    requireBalanced(next.lines());
    requireKey(idempotencyKey);
    requireActor(next.actorUserId());
    if (!groupId.equals(next.groupId())) {
      throw new IllegalArgumentException("the new entry must be in the same group");
    }
    if (!KEYED.contains(next.kind())) {
      throw new LedgerConflict(Reason.NOT_REVERSIBLE);
    }
    return transactions.execute(
        status -> {
          requireMember(groupId, next.actorUserId());
          requireCurrentLineMembers(groupId, next.lines());
          reverseInTransaction(groupId, entryId, next.actorUserId(), idempotencyKey + ":rev");
          return postKeyed(next, null, entryId, idempotencyKey, () -> {});
        });
  }

  private Posted reverseInTransaction(
      UUID groupId, UUID entryId, UUID actorUserId, String idempotencyKey) {
    StoredEntry original =
        journal
            .findInGroup(groupId, entryId, actorUserId)
            .orElseThrow(() -> new LedgerConflict(Reason.NOT_IN_GROUP));
    if (!KEYED.contains(EntryKind.valueOf(original.kind()))) {
      throw new LedgerConflict(Reason.NOT_REVERSIBLE);
    }
    List<JournalLine> negated =
        journal.linesOf(groupId, entryId, actorUserId).stream()
            .map(
                l -> new JournalLine(l.memberId(), l.currency(), Math.negateExact(l.amountMinor())))
            .toList();
    // A member who left, or a guest merged away, no longer takes a balance back.
    requireCurrentLineMembers(groupId, negated);
    JournalEntry reversal = new JournalEntry(groupId, EntryKind.REVERSAL, actorUserId, negated);
    return postKeyed(
        reversal,
        entryId,
        null,
        idempotencyKey,
        () -> {
          if (journal.hasReversal(groupId, entryId, actorUserId)) {
            throw new LedgerConflict(Reason.ALREADY_REVERSED);
          }
        });
  }

  /**
   * Posts once per key. A stored key with the same request hash is a replay; a different hash is a
   * reused key. {@code beforeInsert} runs only when the key is new.
   */
  private Posted postKeyed(
      JournalEntry entry,
      UUID reversesEntryId,
      UUID replacesEntryId,
      String idempotencyKey,
      Runnable beforeInsert) {
    byte[] hash = requestHash(entry, reversesEntryId, replacesEntryId);
    Optional<Posted> replay = replayOf(entry, idempotencyKey, hash);
    if (replay.isPresent()) {
      return replay.get();
    }
    beforeInsert.run();
    UUID id;
    try {
      id = postInTransaction(entry, reversesEntryId, replacesEntryId, idempotencyKey, hash);
    } catch (DuplicateKeyException e) {
      if (reversesEntryId != null) {
        throw new LedgerConflict(Reason.ALREADY_REVERSED);
      }
      throw e;
    }
    if (id == null) {
      // A concurrent post took the key first and has committed.
      return replayOf(entry, idempotencyKey, hash).orElseThrow();
    }
    return new Posted(id, false);
  }

  private Optional<Posted> replayOf(JournalEntry entry, String idempotencyKey, byte[] hash) {
    return journal
        .findByKey(entry.groupId(), idempotencyKey, entry.actorUserId())
        .map(
            stored -> {
              if (!Arrays.equals(stored.requestHash(), hash)) {
                throw new LedgerConflict(Reason.KEY_REUSED);
              }
              return new Posted(stored.id(), true);
            });
  }

  /**
   * SHA-256 of the kind, the actor, the links and the lines in a canonical order. The actor is in
   * it so another member reusing a key gets KEY_REUSED, never a replay of someone else's entry.
   */
  private static byte[] requestHash(JournalEntry entry, UUID reverses, UUID replaces) {
    List<String> parts = new ArrayList<>();
    parts.add(entry.kind().name());
    parts.add(entry.actorUserId() == null ? "" : entry.actorUserId().toString());
    parts.add(reverses == null ? "" : reverses.toString());
    parts.add(replaces == null ? "" : replaces.toString());
    entry.lines().stream()
        .map(l -> l.memberId() + "|" + l.currency() + "|" + l.amountMinor())
        .sorted()
        .forEach(parts::add);
    return IdempotencyService.hash(parts.toArray(String[]::new));
  }

  /** Returns the new entry's id, or null when a keyed insert found its key already taken. */
  private UUID postInTransaction(
      JournalEntry entry,
      UUID reversesEntryId,
      UUID replacesEntryId,
      String idempotencyKey,
      byte[] requestHash) {
    // The group lock comes before any balance row lock, so a repair (exclusive) never interleaves
    // with a post. It is re-entrant, so a write-off or merge that already holds it is fine.
    journal.lockGroupShared(entry.groupId());
    UUID entryId = UUID.randomUUID();
    boolean inserted =
        journal.insertEntry(
            entryId,
            entry.groupId(),
            entry.kind().name(),
            entry.actorUserId(),
            reversesEntryId,
            replacesEntryId,
            idempotencyKey,
            requestHash);
    if (!inserted) {
      return null;
    }
    journal.insertLines(entryId, entry.groupId(), entry.lines());

    // Sorted, so two concurrent posts take their row locks in the same order.
    Map<MemberCurrency, Long> deltas = new TreeMap<>();
    for (JournalLine line : entry.lines()) {
      deltas.merge(
          new MemberCurrency(line.memberId(), line.currency()), line.amountMinor(), Math::addExact);
    }
    deltas.forEach(
        (key, delta) ->
            journal.addToBalance(entry.groupId(), key.memberId(), key.currency(), delta));
    return entryId;
  }

  private static void requireKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("an idempotency key is required");
    }
    if (idempotencyKey.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "an idempotency key is at most " + MAX_KEY_LENGTH + " characters");
    }
  }

  /** A keyed entry is a member's post, so it names the acting user. */
  private static void requireActor(UUID actorUserId) {
    if (actorUserId == null) {
      throw new IllegalArgumentException("a keyed entry needs an acting member");
    }
  }

  /**
   * The actor must be a current member of the group; checked in SQL inside the transaction with a
   * share lock on the member row, taken before the group lock, so a leave cannot slip past a post.
   */
  private void requireMember(UUID groupId, UUID actorUserId) {
    if (!journal.isCurrentMember(groupId, actorUserId)) {
      throw new LedgerConflict(Reason.NOT_A_MEMBER);
    }
  }

  /**
   * Every member a line names must be a current member of the group; checked in SQL inside the
   * transaction with a share lock on each member row, so a leave cannot slip past the post.
   */
  private void requireCurrentLineMembers(UUID groupId, List<JournalLine> lines) {
    Set<UUID> members = lines.stream().map(JournalLine::memberId).collect(Collectors.toSet());
    if (journal.lockCurrentMembers(groupId, members) != members.size()) {
      throw new LedgerConflict(Reason.MEMBER_NOT_CURRENT);
    }
  }

  private record MemberCurrency(UUID memberId, String currency)
      implements Comparable<MemberCurrency> {
    @Override
    public int compareTo(MemberCurrency other) {
      int byMember = memberId.compareTo(other.memberId);
      return byMember != 0 ? byMember : currency.compareTo(other.currency);
    }
  }

  private static void requireBalanced(List<JournalLine> lines) {
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("an entry needs at least one line");
    }
    Map<String, Long> sums = new TreeMap<>();
    for (JournalLine line : lines) {
      if (line == null || line.memberId() == null) {
        throw new IllegalArgumentException("every line needs a member");
      }
      if (line.amountMinor() == 0L) {
        throw new IllegalArgumentException("a line cannot be zero");
      }
      if (line.currency() == null || !CURRENCIES.contains(line.currency())) {
        throw new IllegalArgumentException("a line needs an ISO 4217 currency with minor units");
      }
      sums.merge(line.currency(), line.amountMinor(), Math::addExact);
    }
    if (sums.values().stream().anyMatch(sum -> sum != 0L)) {
      throw new IllegalArgumentException("an entry must sum to zero in every currency");
    }
  }

  @Override
  public List<MoneyBalance> ofMember(UUID groupId, UUID memberId, UUID callerId) {
    return journal.balancesOfMember(groupId, memberId, callerId);
  }

  @Override
  public List<MemberBalance> ofGroup(UUID groupId, UUID callerId) {
    return journal.balancesOfGroup(groupId, callerId);
  }

  @Override
  public List<HistoryEntry> ofGroup(UUID groupId, UUID callerId, Long afterSeq, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be at least 1");
    }
    return journal.history(groupId, callerId, afterSeq, Math.min(limit, MAX_LIMIT));
  }
}
