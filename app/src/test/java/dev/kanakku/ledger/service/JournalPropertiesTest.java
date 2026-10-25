package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.support.AppContext;
import dev.kanakku.support.Seed;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jooq.DSLContext;

/** Property 1: any sequence of posts keeps a group's balances at zero per currency. */
class JournalPropertiesTest {

  static final int MEMBERS = 4;
  static final String[] CURRENCIES = {"INR", "USD", "JPY"};

  /** One transfer: member {@code from} owes member {@code to} an amount in a currency. */
  record Transfer(int from, int to, int currency, long amount) {}

  @Provide
  Arbitrary<List<Transfer>> transfers() {
    Arbitrary<Transfer> one =
        Arbitraries.integers()
            .between(0, MEMBERS - 1)
            .flatMap(
                from ->
                    Arbitraries.integers()
                        .between(0, MEMBERS - 1)
                        .flatMap(
                            to ->
                                Arbitraries.integers()
                                    .between(0, CURRENCIES.length - 1)
                                    .flatMap(
                                        c ->
                                            Arbitraries.longs()
                                                .between(1, 1_000_000_000_000L)
                                                .map(a -> new Transfer(from, to, c, a)))));
    return one.list().ofMinSize(1).ofMaxSize(8);
  }

  @Property(tries = 40)
  void groupBalancesSumToZeroPerCurrencyAfterAnySequenceOfPosts(
      @ForAll("transfers") List<Transfer> transfers) {
    DSLContext dsl = AppContext.bean(DSLContext.class);
    Journal journal = AppContext.bean(Journal.class);
    Balances balances = AppContext.bean(Balances.class);
    List<UUID> ids = Seed.groupWithGuests(dsl, MEMBERS);
    UUID group = ids.get(0);

    for (Transfer t : transfers) {
      journal.post(
          new JournalEntry(
              group,
              EntryKind.EXPENSE,
              null,
              List.of(
                  new JournalLine(ids.get(1 + t.from()), CURRENCIES[t.currency()], t.amount()),
                  new JournalLine(ids.get(1 + t.to()), CURRENCIES[t.currency()], -t.amount()))));
    }

    Map<String, Long> sums = new HashMap<>();
    for (MemberBalance b : balances.ofGroup(group)) {
      sums.merge(b.currency(), b.amountMinor(), Long::sum);
    }
    assertThat(sums.values()).allMatch(sum -> sum == 0L);

    Map<String, Long> fromJournal = new HashMap<>();
    dsl.select(JOURNAL_LINES.MEMBER_ID, JOURNAL_LINES.CURRENCY, JOURNAL_LINES.AMOUNT_MINOR)
        .from(JOURNAL_LINES)
        .where(JOURNAL_LINES.GROUP_ID.eq(group))
        .forEach(r -> fromJournal.merge(r.value1() + "/" + r.value2(), r.value3(), Long::sum));
    Map<String, Long> stored = new HashMap<>();
    balances
        .ofGroup(group)
        .forEach(b -> stored.put(b.memberId() + "/" + b.currency(), b.amountMinor()));
    fromJournal.values().removeIf(v -> v == 0L);
    stored.values().removeIf(v -> v == 0L);
    assertThat(stored).isEqualTo(fromJournal);
  }
}
