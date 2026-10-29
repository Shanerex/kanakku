package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class JournalRollbackTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;
  @MockitoSpyBean JournalRepository repository;

  @Test
  void aFailureAfterTheJournalInsertLeavesNoJournalRowAndNoBalanceChange() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 10L), "before");
    List<MemberBalance> before = balances.ofGroup(g, Seed.caller(dsl, g));
    // The first balance update succeeds, the second throws: the journal rows and that first
    // update are already written in the transaction when it fails.
    doCallRealMethod()
        .doThrow(new IllegalStateException("forced"))
        .when(repository)
        .addToBalance(any(), any(), anyString(), anyLong());

    assertThatThrownBy(
            () ->
                journal.post(
                    JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", b, a, 4L), "boom"))
        .hasMessage("forced");

    verify(repository, times(2)).insertLines(any(), any(), any());
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g))).isEqualTo(1);
    assertThat(
            dsl.fetchCount(
                JOURNAL_LINES,
                JOURNAL_LINES.GROUP_ID.eq(g).and(JOURNAL_LINES.AMOUNT_MINOR.in(4L, -4L))))
        .isZero();
    assertThat(balances.ofGroup(g, Seed.caller(dsl, g))).isEqualTo(before);
  }
}
