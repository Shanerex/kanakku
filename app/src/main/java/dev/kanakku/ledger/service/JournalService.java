package dev.kanakku.ledger.service;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.repository.JournalRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Posts journal entries and reads balances. A post and its balance updates share one transaction,
 * and each group's balances stay at zero per currency because every entry sums to zero.
 */
@Service
class JournalService implements Journal, Balances {

  private final JournalRepository journal;
  private final TransactionTemplate transactions;

  JournalService(JournalRepository journal, TransactionTemplate transactions) {
    this.journal = journal;
    this.transactions = transactions;
  }

  @Override
  public UUID post(JournalEntry entry) {
    requireBalanced(entry.lines());
    return transactions.execute(status -> postInTransaction(entry));
  }

  private UUID postInTransaction(JournalEntry entry) {
    UUID entryId = UUID.randomUUID();
    journal.insertEntry(entryId, entry.groupId(), entry.kind().name(), entry.actorUserId());
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
}
