package dev.kanakku.ledger;

import static dev.kanakku.jooq.Tables.JOURNAL_LINES;
import static org.assertj.core.api.Assertions.assertThat;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.Posted;
import dev.kanakku.ledger.repository.JournalRepository;
import dev.kanakku.ledger.util.LargestRemainder;
import dev.kanakku.support.AppContext;
import dev.kanakku.support.Seed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * The ledger's money properties, driven through {@code ledger.api} against real Postgres: random
 * chains of add, edit, delete, settle and replay in INR, JPY and KWD keep every group at zero per
 * currency, every entry balanced, and the stored balances equal to the journal rebuilt from
 * scratch. One body, two sizes: 1,000 tries locally ({@code ledger-quick}) and 10,000 in CI ({@code
 * ledger-full}).
 */
class LedgerProperties {

  static final String[] CURRENCIES = {"INR", "JPY", "KWD"};
  static final long MAX_AMOUNT = 1_000_000_000_000L;
  static final int MAX_MEMBERS = 6;

  @Property(tries = 1000)
  @Tag("ledger-quick")
  void ledgerInvariantsHoldQuick(@ForAll("chains") ActionChain<LedgerState> chain) {
    check(chain);
  }

  @Property(tries = 10000)
  @Tag("ledger-full")
  void ledgerInvariantsHoldFull(@ForAll("chains") ActionChain<LedgerState> chain) {
    check(chain);
  }

  static void check(ActionChain<LedgerState> chain) {
    chain.withInvariant("ledger invariants", LedgerState::assertInvariants).run();
  }

  @Provide
  Arbitrary<ActionChain<LedgerState>> chains() {
    return Arbitraries.integers()
        .between(2, MAX_MEMBERS)
        .flatMap(
            members ->
                ActionChain.startWith(() -> new LedgerState(members))
                    .withAction(3, new Add())
                    .withAction(2, new Settle())
                    .withAction(2, new Edit())
                    .withAction(2, new Delete())
                    .withAction(1, new Replay())
                    .withMaxTransformations(12));
  }

  /** Reversing an expense returns every member's balance to its prior value (AC-14). */
  @Property(tries = 200)
  void reversingAnExpenseRestoresThePriorBalances(
      @ForAll("specs") List<EntrySpec> before, @ForAll("specs") List<EntrySpec> extra) {
    LedgerState state = new LedgerState(MAX_MEMBERS);
    for (EntrySpec spec : before) {
      state.post(spec);
    }
    Map<String, Long> prior = state.storedNonZero();
    for (EntrySpec spec : extra) {
      state.post(spec);
      state.delete(state.live.size() - 1);
    }
    assertThat(state.storedNonZero()).isEqualTo(prior);
    state.assertInvariants();
  }

  @Provide
  Arbitrary<List<EntrySpec>> specs() {
    return entrySpec().list().ofMaxSize(4);
  }

  /** What to post, in member indexes; resolved against the state's members. */
  record EntrySpec(
      boolean settlement,
      int currency,
      int payer,
      int other,
      List<Integer> participants,
      long amount) {}

  static Arbitrary<EntrySpec> entrySpec() {
    return Combinators.combine(
            Arbitraries.of(true, false),
            Arbitraries.integers().between(0, CURRENCIES.length - 1),
            Arbitraries.integers().between(0, MAX_MEMBERS - 1),
            Arbitraries.integers().between(0, MAX_MEMBERS - 1),
            Arbitraries.integers().between(0, MAX_MEMBERS - 1).list().ofMinSize(1).ofMaxSize(6),
            Arbitraries.longs().between(1, MAX_AMOUNT))
        .as(EntrySpec::new);
  }

  /** A live (posted, not reversed) member entry. */
  record Live(UUID id, JournalEntry entry) {}

  static final class LedgerState {
    final Journal journal = AppContext.bean(Journal.class);
    final Balances balances = AppContext.bean(Balances.class);
    final JournalRepository repository = AppContext.bean(JournalRepository.class);
    final DSLContext dsl = AppContext.bean(DSLContext.class);
    final UUID group;
    final UUID caller;
    final List<UUID> members;
    final List<Live> live = new ArrayList<>();

    /** The model: what each member's balance should be, per currency. */
    final Map<String, Long> expected = new TreeMap<>();

    int keys;
    Supplier<Posted> lastCall;
    UUID lastId;

    LedgerState(int memberCount) {
      List<UUID> ids = Seed.groupWithGuests(dsl, memberCount);
      group = ids.get(0);
      members = List.copyOf(ids.subList(1, ids.size()));
      caller = Seed.caller(dsl, group);
    }

    String nextKey() {
      return "k" + keys++;
    }

    JournalEntry entryOf(EntrySpec spec) {
      String currency = CURRENCIES[spec.currency()];
      UUID payer = members.get(spec.payer() % members.size());
      if (spec.settlement()) {
        int to = spec.other() % members.size();
        if (to == spec.payer() % members.size()) {
          to = (to + 1) % members.size();
        }
        return JournalEntry.settlement(
            group, caller, currency, payer, members.get(to), spec.amount());
      }
      // Distinct participants in stable member order; the remainder goes by largest remainder.
      TreeSet<Integer> picked = new TreeSet<>();
      spec.participants().forEach(i -> picked.add(i % members.size()));
      long[] shares = LargestRemainder.split(spec.amount(), picked.size());
      LinkedHashMap<UUID, Long> byMember = new LinkedHashMap<>();
      int k = 0;
      for (int index : picked) {
        byMember.put(members.get(index), shares[k++]);
      }
      return JournalEntry.expense(group, caller, currency, payer, byMember);
    }

    void apply(JournalEntry entry, int sign) {
      entry
          .lines()
          .forEach(
              l ->
                  expected.merge(
                      l.memberId() + "/" + l.currency(),
                      Math.multiplyExact(sign, l.amountMinor()),
                      Math::addExact));
    }

    UUID post(EntrySpec spec) {
      JournalEntry entry = entryOf(spec);
      String key = nextKey();
      Posted posted = journal.post(entry, key);
      assertThat(posted.replayed()).isFalse();
      live.add(new Live(posted.entryId(), entry));
      apply(entry, 1);
      lastCall = () -> journal.post(entry, key);
      lastId = posted.entryId();
      return posted.entryId();
    }

    void edit(int index, EntrySpec spec) {
      Live old = live.remove(index % live.size());
      JournalEntry next = entryOf(spec);
      String key = nextKey();
      Posted posted = journal.replace(group, old.id(), next, key);
      assertThat(posted.replayed()).isFalse();
      apply(old.entry(), -1);
      apply(next, 1);
      live.add(new Live(posted.entryId(), next));
      lastCall = () -> journal.replace(group, old.id(), next, key);
      lastId = posted.entryId();
    }

    void delete(int index) {
      Live old = live.remove(index % live.size());
      String key = nextKey();
      Posted posted = journal.reverse(group, old.id(), caller, key);
      assertThat(posted.replayed()).isFalse();
      apply(old.entry(), -1);
      lastCall = () -> journal.reverse(group, old.id(), caller, key);
      lastId = posted.entryId();
    }

    void replay() {
      Map<String, Long> before = storedNonZero();
      assertThat(lastCall.get()).isEqualTo(new Posted(lastId, true));
      assertThat(storedNonZero()).isEqualTo(before);
    }

    Map<String, Long> storedNonZero() {
      Map<String, Long> stored = new TreeMap<>();
      for (MemberBalance b : balances.ofGroup(group, caller)) {
        if (b.amountMinor() != 0L) {
          stored.put(b.memberId() + "/" + b.currency(), b.amountMinor());
        }
      }
      return stored;
    }

    void assertInvariants() {
      // AC-1: zero-sum per currency.
      Map<String, Long> sums = new HashMap<>();
      for (MemberBalance b : balances.ofGroup(group, caller)) {
        sums.merge(b.currency(), b.amountMinor(), Math::addExact);
      }
      assertThat(sums.values()).allMatch(sum -> sum == 0L);
      // AC-2: every entry's debits equal its credits, per currency.
      assertThat(
              dsl.fetchCount(
                  dsl.select(JOURNAL_LINES.ENTRY_ID)
                      .from(JOURNAL_LINES)
                      .where(JOURNAL_LINES.GROUP_ID.eq(group))
                      .groupBy(JOURNAL_LINES.ENTRY_ID, JOURNAL_LINES.CURRENCY)
                      .having(
                          DSL.sum(JOURNAL_LINES.AMOUNT_MINOR)
                              .ne(DSL.inline(java.math.BigDecimal.ZERO)))))
          .isZero();
      // AC-3: balances rebuilt from the journal equal the stored ones exactly.
      assertThat(repository.mismatchesOf(group)).isEmpty();
      // And both equal the model.
      Map<String, Long> model = new TreeMap<>(expected);
      model.values().removeIf(v -> v == 0L);
      assertThat(storedNonZero()).isEqualTo(model);
    }
  }

  static final class Add implements Action.Independent<LedgerState> {
    @Override
    public Arbitrary<Transformer<LedgerState>> transformer() {
      return entrySpec()
          .filter(spec -> !spec.settlement())
          .map(spec -> Transformer.mutate("add " + spec, state -> state.post(spec)));
    }
  }

  static final class Settle implements Action.Independent<LedgerState> {
    @Override
    public Arbitrary<Transformer<LedgerState>> transformer() {
      return entrySpec()
          .filter(EntrySpec::settlement)
          .map(spec -> Transformer.mutate("settle " + spec, state -> state.post(spec)));
    }
  }

  static final class Edit implements Action.Independent<LedgerState> {
    @Override
    public boolean precondition(LedgerState state) {
      return !state.live.isEmpty();
    }

    @Override
    public Arbitrary<Transformer<LedgerState>> transformer() {
      return Combinators.combine(Arbitraries.integers().between(0, 100), entrySpec())
          .as(
              (index, spec) ->
                  Transformer.mutate(
                      "edit " + index + " to " + spec, state -> state.edit(index, spec)));
    }
  }

  static final class Delete implements Action.Independent<LedgerState> {
    @Override
    public boolean precondition(LedgerState state) {
      return !state.live.isEmpty();
    }

    @Override
    public Arbitrary<Transformer<LedgerState>> transformer() {
      return Arbitraries.integers()
          .between(0, 100)
          .map(index -> Transformer.mutate("delete " + index, state -> state.delete(index)));
    }
  }

  static final class Replay implements Action.Independent<LedgerState> {
    @Override
    public boolean precondition(LedgerState state) {
      return state.lastCall != null;
    }

    @Override
    public Arbitrary<Transformer<LedgerState>> transformer() {
      return Arbitraries.just(Transformer.mutate("replay last", LedgerState::replay));
    }
  }
}
