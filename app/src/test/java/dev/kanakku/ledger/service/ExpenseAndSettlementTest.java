package dev.kanakku.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExpenseAndSettlementTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;

  static String key() {
    return UUID.randomUUID().toString();
  }

  @Test
  void anExpensePaidByACreditsThePayerAndDebitsEachShare() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    LinkedHashMap<UUID, Long> shares = new LinkedHashMap<>();
    shares.put(a, 334L);
    shares.put(b, 333L);
    shares.put(c, 333L);

    JournalEntry expense = JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares);
    assertThat(expense.kind()).isEqualTo(EntryKind.EXPENSE);
    journal.post(expense, key());

    UUID caller = Seed.caller(dsl, g);
    assertThat(balances.ofMember(g, a, caller)).containsExactly(new MoneyBalance("INR", 666));
    assertThat(balances.ofMember(g, b, caller)).containsExactly(new MoneyBalance("INR", -333));
    assertThat(balances.ofMember(g, c, caller)).containsExactly(new MoneyBalance("INR", -333));
  }

  @Test
  void aSettlementFromBToAMovesBUpAndADownAndKeepsTheGroupAtZero() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    LinkedHashMap<UUID, Long> shares = new LinkedHashMap<>();
    shares.put(b, 500L);
    shares.put(c, 500L);
    journal.post(JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares), key());

    JournalEntry settlement = JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", b, a, 200L);
    assertThat(settlement.kind()).isEqualTo(EntryKind.SETTLEMENT);
    journal.post(settlement, key());

    UUID caller = Seed.caller(dsl, g);
    assertThat(balances.ofMember(g, b, caller)).containsExactly(new MoneyBalance("INR", -300));
    assertThat(balances.ofMember(g, a, caller)).containsExactly(new MoneyBalance("INR", 800));
    assertThat(balances.ofGroup(g, caller).stream().mapToLong(MemberBalance::amountMinor).sum())
        .isZero();
  }

  @Test
  void badSharesAndSettlementsAreRefusedBeforeAnythingIsWritten() {
    UUID g = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    LinkedHashMap<UUID, Long> negative = new LinkedHashMap<>();
    negative.put(b, -1L);
    LinkedHashMap<UUID, Long> zero = new LinkedHashMap<>();
    zero.put(b, 0L);

    assertThatThrownBy(() -> JournalEntry.expense(g, null, "INR", a, negative))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> JournalEntry.expense(g, null, "INR", a, zero))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> JournalEntry.settlement(g, null, "INR", a, b, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> JournalEntry.settlement(g, null, "INR", a, a, 5L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
