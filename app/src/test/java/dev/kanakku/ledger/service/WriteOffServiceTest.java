package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.api.WriteOffs;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WriteOffServiceTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired WriteOffs writeOffs;
  @Autowired DSLContext dsl;

  void owe(UUID group, UUID creditor, UUID debtor, String currency, long amount) {
    journal.post(
        new JournalEntry(
            group,
            EntryKind.EXPENSE,
            null,
            List.of(
                new JournalLine(creditor, currency, amount),
                new JournalLine(debtor, currency, -amount))));
  }

  @Test
  void aDebtorsBalanceIsSplitOverTheAbsorbersAndTheTargetEndsAtZero() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 4);
    UUID g = ids.get(0), payer = ids.get(1), debtor = ids.get(2), a1 = ids.get(3), a2 = ids.get(4);
    owe(g, payer, debtor, "INR", 1000);

    List<MoneyBalance> written = writeOffs.writeOff(g, debtor, List.of(a1, a2, payer), null);

    assertThat(written).containsExactly(new MoneyBalance("INR", -1000));
    assertThat(balances.ofMember(g, debtor, Seed.caller(dsl, g)))
        .containsExactly(new MoneyBalance("INR", 0));
    // -1000 over three absorbers in order a1, a2, payer: the first one takes the extra unit.
    assertThat(balances.ofMember(g, a1, Seed.caller(dsl, g)))
        .containsExactly(new MoneyBalance("INR", -334));
    assertThat(balances.ofMember(g, a2, Seed.caller(dsl, g)))
        .containsExactly(new MoneyBalance("INR", -333));
    assertThat(balances.ofMember(g, payer, Seed.caller(dsl, g)))
        .containsExactly(new MoneyBalance("INR", 1000 - 333));
  }

  @Test
  void aCreditorsBalanceIsWrittenOffOntoTheAbsorberToo() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), creditor = ids.get(1), debtor = ids.get(2), absorber = ids.get(3);
    owe(g, creditor, debtor, "INR", 500);

    writeOffs.writeOff(g, creditor, List.of(absorber), Seed.user(dsl, "Owner"));

    assertThat(balances.ofMember(g, creditor, Seed.caller(dsl, g)))
        .containsExactly(new MoneyBalance("INR", 0));
    assertThat(balances.ofMember(g, absorber, Seed.caller(dsl, g)))
        .containsExactly(new MoneyBalance("INR", 500));
  }

  @Test
  void eachCurrencyGetsItsOwnWriteOffEntryAndZeroBalancesAreLeftAlone() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), payer = ids.get(1), debtor = ids.get(2), absorber = ids.get(3);
    owe(g, payer, debtor, "INR", 300);
    owe(g, payer, debtor, "USD", 40);
    owe(g, debtor, payer, "EUR", 25);
    owe(g, payer, debtor, "EUR", 25);

    List<MoneyBalance> written = writeOffs.writeOff(g, debtor, List.of(absorber), null);

    assertThat(written)
        .containsExactlyInAnyOrder(new MoneyBalance("INR", -300), new MoneyBalance("USD", -40));
    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES
                    .GROUP_ID
                    .eq(g)
                    .and(JOURNAL_ENTRIES.KIND.eq(EntryKind.WRITE_OFF.name()))))
        .isEqualTo(2);
  }

  @Test
  void aMemberAtZeroWritesNothing() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);

    assertThat(writeOffs.writeOff(ids.get(0), ids.get(1), List.of(ids.get(2)), null)).isEmpty();
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(ids.get(0)))).isZero();
  }

  @Test
  void badAbsorberListsAreRefused() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), target = ids.get(1), other = ids.get(2);

    assertThatThrownBy(() -> writeOffs.writeOff(g, target, List.of(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> writeOffs.writeOff(g, target, List.of(target), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> writeOffs.writeOff(g, target, List.of(other, other), null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
