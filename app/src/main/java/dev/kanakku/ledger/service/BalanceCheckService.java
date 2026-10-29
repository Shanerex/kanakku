package dev.kanakku.ledger.service;

import dev.kanakku.ledger.config.BalanceCheckProperties;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.ledger.repository.JournalRepository.Mismatch;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rebuilds balances from the journal and compares them to the stored ones. Each mismatch is
 * reported (one ERROR line and one count per group and currency, with no amounts); then, when
 * repair is on, the mismatched rows are overwritten with their journal sums. The journal stays the
 * truth.
 */
@Service
public class BalanceCheckService {

  public static final String METRIC = "kanakku.ledger.balance.mismatches";
  static final String MESSAGE = "ledger balance mismatch";

  private static final Logger log = LoggerFactory.getLogger(BalanceCheckService.class);

  private final JournalRepository journal;
  private final TransactionTemplate transactions;
  private final Counter mismatches;
  private final boolean repair;

  public BalanceCheckService(
      JournalRepository journal,
      TransactionTemplate transactions,
      MeterRegistry meters,
      BalanceCheckProperties properties) {
    this.journal = journal;
    this.transactions = transactions;
    this.mismatches =
        Counter.builder(METRIC)
            .description("Member balances found differing from the journal, per group and currency")
            .register(meters);
    this.repair = properties.repair();
  }

  /** Checks every group whose balances differ from the journal, each in its own transaction. */
  public void run() {
    for (UUID groupId : journal.mismatchedGroups()) {
      try {
        transactions.executeWithoutResult(status -> checkGroup(groupId));
      } catch (RuntimeException e) {
        log.atError()
            .setCause(e)
            .addKeyValue("groupId", groupId)
            .log("ledger balance check failed for a group");
      }
    }
  }

  private void checkGroup(UUID groupId) {
    if (repair) {
      // Excludes every post to the group until commit; the recheck below then sees all of them.
      journal.lockGroupExclusive(groupId);
    }
    List<Mismatch> rows = journal.mismatchesOf(groupId);
    if (rows.isEmpty()) {
      return;
    }
    Map<String, Integer> membersByCurrency = new TreeMap<>();
    rows.forEach(row -> membersByCurrency.merge(row.currency(), 1, Integer::sum));
    membersByCurrency.forEach(
        (currency, members) -> {
          log.atError()
              .addKeyValue("groupId", groupId)
              .addKeyValue("currency", currency)
              .addKeyValue("members", members)
              .addKeyValue("repaired", repair)
              .log(MESSAGE);
          mismatches.increment();
        });
    if (repair) {
      journal.repairBalances(groupId, rows);
    }
  }
}
