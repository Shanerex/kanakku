package dev.kanakku.ledger.service;

import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.api.WriteOffs;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.ledger.util.LargestRemainder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Moves a member's balance onto other members, one visible journal entry per currency. */
@Service
class WriteOffService implements WriteOffs {

  private final JournalRepository repository;
  private final Journal journal;
  private final TransactionTemplate transactions;

  WriteOffService(JournalRepository repository, Journal journal, TransactionTemplate transactions) {
    this.repository = repository;
    this.journal = journal;
    this.transactions = transactions;
  }

  @Override
  public List<MoneyBalance> writeOff(
      UUID groupId, UUID memberId, List<UUID> absorberIds, UUID actorUserId) {
    if (absorberIds == null || absorberIds.isEmpty()) {
      throw new IllegalArgumentException("a write-off needs at least one absorber");
    }
    if (absorberIds.contains(memberId)) {
      throw new IllegalArgumentException("a member cannot absorb their own write-off");
    }
    if (new HashSet<>(absorberIds).size() != absorberIds.size()) {
      throw new IllegalArgumentException("absorbers must be distinct");
    }
    return transactions.execute(
        status -> writeOffInTransaction(groupId, memberId, absorberIds, actorUserId));
  }

  private List<MoneyBalance> writeOffInTransaction(
      UUID groupId, UUID memberId, List<UUID> absorberIds, UUID actorUserId) {
    // The group's ledger lock first, before any balance row, as every post takes it.
    repository.lockGroupShared(groupId);
    List<MoneyBalance> written = new ArrayList<>();
    // The balance rows of the target and the absorbers stay locked until commit, so no post can
    // slip in between the read and the entry. A post locks its rows sorted by member id; taking
    // these in the same order keeps a write-off and a post from waiting on each other.
    List<UUID> lockOrder = new ArrayList<>(absorberIds);
    lockOrder.add(memberId);
    Collections.sort(lockOrder);
    List<MoneyBalance> targetBalances = List.of();
    for (UUID id : lockOrder) {
      List<MoneyBalance> locked = repository.lockBalancesOfMember(groupId, id);
      if (id.equals(memberId)) {
        targetBalances = locked;
      }
    }
    for (MoneyBalance balance : targetBalances) {
      long amount = balance.amountMinor();
      if (amount == 0L) {
        continue;
      }
      long[] shares = LargestRemainder.split(amount, absorberIds.size());
      List<JournalLine> lines = new ArrayList<>();
      lines.add(new JournalLine(memberId, balance.currency(), Math.negateExact(amount)));
      for (int i = 0; i < shares.length; i++) {
        if (shares[i] != 0L) {
          lines.add(new JournalLine(absorberIds.get(i), balance.currency(), shares[i]));
        }
      }
      journal.post(new JournalEntry(groupId, EntryKind.WRITE_OFF, actorUserId, lines));
      written.add(balance);
    }
    return written;
  }
}
