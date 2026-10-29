package dev.kanakku.ledger.service;

import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberMerges;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.repository.JournalRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Moves a guest's balance onto another member, one visible journal entry per currency. */
@Service
class MemberMergeService implements MemberMerges {

  private final JournalRepository repository;
  private final Journal journal;
  private final TransactionTemplate transactions;

  MemberMergeService(
      JournalRepository repository, Journal journal, TransactionTemplate transactions) {
    this.repository = repository;
    this.journal = journal;
    this.transactions = transactions;
  }

  @Override
  public List<MoneyBalance> merge(
      UUID groupId, UUID fromMemberId, UUID intoMemberId, UUID actorUserId) {
    if (fromMemberId.equals(intoMemberId)) {
      throw new IllegalArgumentException("a member cannot be merged into themself");
    }
    return transactions.execute(
        status -> mergeInTransaction(groupId, fromMemberId, intoMemberId, actorUserId));
  }

  private List<MoneyBalance> mergeInTransaction(
      UUID groupId, UUID fromMemberId, UUID intoMemberId, UUID actorUserId) {
    // The group's ledger lock first, before any balance row, as every post takes it.
    repository.lockGroupShared(groupId);
    // Both members' balance rows stay locked until commit, taken in sorted order like a post and a
    // write-off do, so none of them can wait on another.
    List<MoneyBalance> fromBalances = List.of();
    for (UUID id : List.of(fromMemberId, intoMemberId).stream().sorted().toList()) {
      List<MoneyBalance> locked = repository.lockBalancesOfMember(groupId, id);
      if (id.equals(fromMemberId)) {
        fromBalances = locked;
      }
    }
    List<MoneyBalance> moved = new ArrayList<>();
    for (MoneyBalance balance : fromBalances) {
      long amount = balance.amountMinor();
      if (amount == 0L) {
        continue;
      }
      journal.post(
          new JournalEntry(
              groupId,
              EntryKind.MERGE,
              actorUserId,
              List.of(
                  new JournalLine(fromMemberId, balance.currency(), Math.negateExact(amount)),
                  new JournalLine(intoMemberId, balance.currency(), amount))));
      moved.add(balance);
    }
    return moved;
  }
}
