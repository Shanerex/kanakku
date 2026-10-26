package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MemberMerges;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.support.AppContext;
import dev.kanakku.support.Seed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.jooq.DSLContext;

/** Properties of the claim merge, against the real journal and balances. */
class MemberMergePropertiesTest {

  static final long MAX = 1_000_000_000_000L;
  static final String[] CURRENCIES = {"INR", "USD", "JPY"};

  record Setup(
      UUID group,
      UUID guest,
      UUID into,
      UUID other,
      Map<String, Long> guestBalance,
      Map<String, Long> intoBalance) {}

  /**
   * Gives {@code member} the balance {@code amount} in the currency against {@code counterparty}.
   */
  static void balance(UUID group, UUID member, UUID counterparty, String currency, long amount) {
    if (amount == 0L) {
      return;
    }
    AppContext.bean(Journal.class)
        .post(
            new JournalEntry(
                group,
                EntryKind.EXPENSE,
                null,
                List.of(
                    new JournalLine(member, currency, amount),
                    new JournalLine(counterparty, currency, -amount))));
  }

  static Setup setUp(long[] guestAmounts, long[] intoAmounts) {
    DSLContext dsl = AppContext.bean(DSLContext.class);
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID group = ids.get(0), guest = ids.get(1), into = ids.get(2), other = ids.get(3);
    Map<String, Long> guestBalance = new TreeMap<>();
    Map<String, Long> intoBalance = new TreeMap<>();
    for (int i = 0; i < CURRENCIES.length; i++) {
      balance(group, guest, other, CURRENCIES[i], guestAmounts[i]);
      balance(group, into, other, CURRENCIES[i], intoAmounts[i]);
      guestBalance.put(CURRENCIES[i], guestAmounts[i]);
      intoBalance.put(CURRENCIES[i], intoAmounts[i]);
    }
    return new Setup(group, guest, into, other, guestBalance, intoBalance);
  }

  static long balanceOf(Setup s, UUID member, String currency) {
    UUID caller = Seed.caller(AppContext.bean(DSLContext.class), s.group());
    return AppContext.bean(Balances.class).ofMember(s.group(), member, caller).stream()
        .filter(b -> b.currency().equals(currency))
        .mapToLong(MoneyBalance::amountMinor)
        .sum();
  }

  @Property(tries = 40)
  void aMergeLeavesTheGuestAtZeroAndMovesExactlyTheirBalanceOntoTheMember(
      @ForAll @LongRange(min = -MAX, max = MAX) long guestInr,
      @ForAll @LongRange(min = -MAX, max = MAX) long guestUsd,
      @ForAll @LongRange(min = -MAX, max = MAX) long guestJpy,
      @ForAll @LongRange(min = -MAX, max = MAX) long intoInr,
      @ForAll @LongRange(min = -MAX, max = MAX) long intoUsd) {
    Setup s = setUp(new long[] {guestInr, guestUsd, guestJpy}, new long[] {intoInr, intoUsd, 0});

    List<MoneyBalance> moved =
        AppContext.bean(MemberMerges.class).merge(s.group(), s.guest(), s.into(), null);

    for (String currency : CURRENCIES) {
      assertThat(balanceOf(s, s.guest(), currency)).as("guest in " + currency).isZero();
      assertThat(balanceOf(s, s.into(), currency))
          .as("member in " + currency)
          .isEqualTo(s.guestBalance().get(currency) + s.intoBalance().get(currency));
    }
    List<MoneyBalance> expected = new ArrayList<>();
    s.guestBalance()
        .forEach(
            (c, b) -> {
              if (b != 0L) {
                expected.add(new MoneyBalance(c, b));
              }
            });
    assertThat(moved).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Property(tries = 40)
  void aMergeKeepsTheGroupAtZeroPerCurrency(
      @ForAll @LongRange(min = -MAX, max = MAX) long guestInr,
      @ForAll @LongRange(min = -MAX, max = MAX) long guestUsd,
      @ForAll @LongRange(min = -MAX, max = MAX) long intoInr) {
    Setup s = setUp(new long[] {guestInr, guestUsd, 0}, new long[] {intoInr, 0, 0});

    AppContext.bean(MemberMerges.class).merge(s.group(), s.guest(), s.into(), null);

    UUID caller = Seed.caller(AppContext.bean(DSLContext.class), s.group());
    Map<String, Long> sums = new TreeMap<>();
    for (MemberBalance b : AppContext.bean(Balances.class).ofGroup(s.group(), caller)) {
      sums.merge(b.currency(), b.amountMinor(), Long::sum);
    }
    assertThat(sums.values()).allMatch(sum -> sum == 0L);
  }

  @Property(tries = 40)
  void aMergePostsOneBalancedMergeEntryPerCurrencyThatHadABalance(
      @ForAll @LongRange(min = -MAX, max = MAX) long guestInr,
      @ForAll @LongRange(min = -MAX, max = MAX) long guestUsd,
      @ForAll @LongRange(min = -MAX, max = MAX) long guestJpy) {
    Setup s = setUp(new long[] {guestInr, guestUsd, guestJpy}, new long[] {0, 0, 0});
    DSLContext dsl = AppContext.bean(DSLContext.class);
    int before = dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(s.group()));

    AppContext.bean(MemberMerges.class).merge(s.group(), s.guest(), s.into(), null);

    var merges =
        dsl.select(JOURNAL_ENTRIES.ID)
            .from(JOURNAL_ENTRIES)
            .where(JOURNAL_ENTRIES.GROUP_ID.eq(s.group()))
            .and(JOURNAL_ENTRIES.KIND.eq(EntryKind.MERGE.name()))
            .fetch(JOURNAL_ENTRIES.ID);
    long withBalance = s.guestBalance().values().stream().filter(b -> b != 0L).count();
    assertThat(merges).hasSize((int) withBalance);
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(s.group())))
        .isEqualTo(before + (int) withBalance);
    for (UUID entry : merges) {
      var lines =
          dsl.select(JOURNAL_LINES.MEMBER_ID, JOURNAL_LINES.CURRENCY, JOURNAL_LINES.AMOUNT_MINOR)
              .from(JOURNAL_LINES)
              .where(JOURNAL_LINES.ENTRY_ID.eq(entry))
              .fetch();
      assertThat(lines).hasSize(2);
      assertThat(lines.stream().mapToLong(r -> r.value3()).sum()).isZero();
      assertThat(lines.stream().map(r -> r.value2()).distinct()).hasSize(1);
      assertThat(lines.stream().map(r -> r.value1()))
          .containsExactlyInAnyOrder(s.guest(), s.into());
    }
  }

  @Property(tries = 20)
  void mergingAGuestWithNothingOwedPostsNothing(
      @ForAll @LongRange(min = -MAX, max = MAX) long intoInr) {
    Setup s = setUp(new long[] {0, 0, 0}, new long[] {intoInr, 0, 0});
    DSLContext dsl = AppContext.bean(DSLContext.class);
    int before = dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(s.group()));

    List<MoneyBalance> moved =
        AppContext.bean(MemberMerges.class).merge(s.group(), s.guest(), s.into(), null);

    assertThat(moved).isEmpty();
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(s.group())))
        .isEqualTo(before);
    assertThat(balanceOf(s, s.into(), "INR")).isEqualTo(intoInr);
  }

  @Property(tries = 20)
  void mergingTwiceMovesNothingTheSecondTime(
      @ForAll @LongRange(min = -MAX, max = MAX) long guestInr) {
    Setup s = setUp(new long[] {guestInr, 0, 0}, new long[] {0, 0, 0});
    MemberMerges merges = AppContext.bean(MemberMerges.class);
    merges.merge(s.group(), s.guest(), s.into(), null);

    assertThat(merges.merge(s.group(), s.guest(), s.into(), null)).isEmpty();
    assertThat(balanceOf(s, s.into(), "INR")).isEqualTo(guestInr);
  }
}
