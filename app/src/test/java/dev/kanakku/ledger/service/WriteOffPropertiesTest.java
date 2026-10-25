package dev.kanakku.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.ledger.api.WriteOffs;
import dev.kanakku.support.AppContext;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.jooq.DSLContext;

/** Properties 3 and 5 of the write-off, against the real journal and balances. */
class WriteOffPropertiesTest {

  static final long MAX = 1_000_000_000_000L;

  record Setup(UUID group, UUID target, List<UUID> absorbers) {}

  UUID caller(Setup s) {
    return Seed.caller(AppContext.bean(DSLContext.class), s.group());
  }

  Setup setUp(long balance, int absorberCount) {
    DSLContext dsl = AppContext.bean(DSLContext.class);
    List<UUID> ids = Seed.groupWithGuests(dsl, 2 + absorberCount);
    UUID group = ids.get(0), target = ids.get(1), counterparty = ids.get(2);
    AppContext.bean(Journal.class)
        .post(
            new JournalEntry(
                group,
                EntryKind.EXPENSE,
                null,
                List.of(
                    new JournalLine(target, "INR", balance),
                    new JournalLine(counterparty, "INR", -balance))));
    return new Setup(group, target, ids.subList(3, ids.size()));
  }

  @Property(tries = 40)
  void property5_afterAWriteOffTheTargetIsAtZeroAndTheGroupStillSumsToZero(
      @ForAll @LongRange(min = -MAX, max = MAX) long balance,
      @ForAll @IntRange(min = 1, max = 6) int absorberCount) {
    Setup s = setUp(balance, absorberCount);
    Balances balances = AppContext.bean(Balances.class);

    List<MoneyBalance> written =
        AppContext.bean(WriteOffs.class).writeOff(s.group(), s.target(), s.absorbers(), null);

    assertThat(written)
        .isEqualTo(balance == 0 ? List.of() : List.of(new MoneyBalance("INR", balance)));
    assertThat(balances.ofMember(s.group(), s.target(), caller(s)))
        .allMatch(b -> b.amountMinor() == 0L);
    assertThat(
            balances.ofGroup(s.group(), caller(s)).stream()
                .mapToLong(MemberBalance::amountMinor)
                .sum())
        .isZero();
  }

  @Property(tries = 40)
  void property3_aSingleAbsorberTakesTheWholeBalance(
      @ForAll @LongRange(min = -MAX, max = MAX) long balance) {
    Setup s = setUp(balance, 1);

    AppContext.bean(WriteOffs.class).writeOff(s.group(), s.target(), s.absorbers(), null);

    long absorbed =
        AppContext.bean(Balances.class)
            .ofMember(s.group(), s.absorbers().get(0), caller(s))
            .stream()
            .mapToLong(MoneyBalance::amountMinor)
            .sum();
    assertThat(absorbed).isEqualTo(balance);
  }

  @Property(tries = 40)
  void absorbersEndWithinOneMinorUnitOfEachOther(
      @ForAll @LongRange(min = -MAX, max = MAX) long balance,
      @ForAll @IntRange(min = 2, max = 6) int absorberCount) {
    Setup s = setUp(balance, absorberCount);

    AppContext.bean(WriteOffs.class).writeOff(s.group(), s.target(), s.absorbers(), null);

    long[] parts =
        s.absorbers().stream()
            .mapToLong(
                id ->
                    AppContext.bean(Balances.class).ofMember(s.group(), id, caller(s)).stream()
                        .mapToLong(MoneyBalance::amountMinor)
                        .sum())
            .toArray();
    assertThat(LongStream.of(parts).max().orElseThrow() - LongStream.of(parts).min().orElseThrow())
        .isLessThanOrEqualTo(1);
    assertThat(LongStream.of(parts).sum()).isEqualTo(balance);
  }
}
