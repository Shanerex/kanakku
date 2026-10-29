package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.LedgerConflict;
import dev.kanakku.ledger.api.LedgerConflict.Reason;
import dev.kanakku.ledger.api.MemberBalance;
import dev.kanakku.ledger.api.Posted;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JournalReversalTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired DSLContext dsl;

  static LinkedHashMap<UUID, Long> shares(UUID a, long sa, UUID b, long sb) {
    LinkedHashMap<UUID, Long> shares = new LinkedHashMap<>();
    shares.put(a, sa);
    shares.put(b, sb);
    return shares;
  }

  static void assertConflict(ThrowingCallable call, Reason reason) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            LedgerConflict.class, e -> assertThat(e.reason()).isEqualTo(reason));
  }

  List<MemberBalance> balancesOf(UUID g) {
    return balances.ofGroup(g, Seed.caller(dsl, g));
  }

  int entries(UUID g) {
    return dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.GROUP_ID.eq(g));
  }

  @Test
  void deletingAnExpenseReturnsEveryBalanceToItsPriorValueWithOneReversal() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    journal.post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", c, a, 70L), "s");
    List<MemberBalance> before = balancesOf(g);
    UUID expense =
        journal
            .post(
                JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares(b, 300L, c, 200L)),
                "e")
            .entryId();

    Posted reversal = journal.reverse(g, expense, Seed.caller(dsl, g), "e:del");

    assertThat(perMember(balancesOf(g))).isEqualTo(perMember(before)).containsKeys(a, c);
    assertThat(entries(g)).isEqualTo(3);
    var row = dsl.fetchOne(JOURNAL_ENTRIES, JOURNAL_ENTRIES.ID.eq(reversal.entryId()));
    assertThat(row.getKind()).isEqualTo("REVERSAL");
    assertThat(row.getReversesEntryId()).isEqualTo(expense);
    // Replaying the delete changes nothing.
    assertThat(journal.reverse(g, expense, Seed.caller(dsl, g), "e:del"))
        .isEqualTo(new Posted(reversal.entryId(), true));
    assertThat(entries(g)).isEqualTo(3);
  }

  /** Each member's non-zero balances; a reversal leaves a stored zero row behind. */
  static java.util.Map<UUID, Long> perMember(List<MemberBalance> balances) {
    java.util.Map<UUID, Long> map = new java.util.HashMap<>();
    balances.stream()
        .filter(b -> b.amountMinor() != 0L)
        .forEach(b -> map.merge(b.memberId(), b.amountMinor(), Math::addExact));
    return map;
  }

  @Test
  void anEntryIsReversedAtMostOnce() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID s =
        journal
            .post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 9L), "s")
            .entryId();
    journal.reverse(g, s, Seed.caller(dsl, g), "first");
    List<MemberBalance> after = balancesOf(g);

    assertConflict(
        () -> journal.reverse(g, s, Seed.caller(dsl, g), "second"), Reason.ALREADY_REVERSED);
    assertThat(entries(g)).isEqualTo(2);
    assertThat(balancesOf(g)).isEqualTo(after);
  }

  @Test
  void onlyExpensesAndSettlementsOfTheSameGroupCanBeReversed() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID other = Seed.groupWithGuests(dsl, 0).get(0);
    UUID s =
        journal
            .post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 9L), "s")
            .entryId();
    UUID writeOff =
        journal.post(
            new JournalEntry(
                g,
                EntryKind.WRITE_OFF,
                null,
                List.of(new JournalLine(a, "INR", -9L), new JournalLine(b, "INR", 9L))));
    UUID reversal = journal.reverse(g, s, Seed.caller(dsl, g), "r").entryId();

    assertConflict(
        () -> journal.reverse(other, s, Seed.caller(dsl, other), "x"), Reason.NOT_IN_GROUP);
    assertConflict(
        () -> journal.reverse(g, UUID.randomUUID(), Seed.caller(dsl, g), "y"), Reason.NOT_IN_GROUP);
    assertConflict(
        () -> journal.reverse(g, writeOff, Seed.caller(dsl, g), "z"), Reason.NOT_REVERSIBLE);
    assertConflict(
        () -> journal.reverse(g, reversal, Seed.caller(dsl, g), "w"), Reason.NOT_REVERSIBLE);
    assertThat(entries(g)).isEqualTo(3);
  }

  @Test
  void anEditReversesTheOriginalAndPostsTheNewEntryLinkedToIt() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2), c = ids.get(3);
    UUID original =
        journal
            .post(
                JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares(b, 100L, c, 100L)),
                "e")
            .entryId();
    JournalEntry next =
        JournalEntry.expense(g, Seed.caller(dsl, g), "INR", a, shares(b, 150L, c, 50L));

    Posted edited = journal.replace(g, original, next, "e:edit");

    assertThat(edited.replayed()).isFalse();
    var row = dsl.fetchOne(JOURNAL_ENTRIES, JOURNAL_ENTRIES.ID.eq(edited.entryId()));
    assertThat(row.getReplacesEntryId()).isEqualTo(original);
    assertThat(row.getKind()).isEqualTo("EXPENSE");
    assertThat(dsl.fetchCount(JOURNAL_ENTRIES, JOURNAL_ENTRIES.REVERSES_ENTRY_ID.eq(original)))
        .isEqualTo(1);
    UUID caller = Seed.caller(dsl, g);
    assertThat(balances.ofMember(g, a, caller)).extracting("amountMinor").containsExactly(200L);
    assertThat(balances.ofMember(g, b, caller)).extracting("amountMinor").containsExactly(-150L);
    assertThat(balances.ofMember(g, c, caller)).extracting("amountMinor").containsExactly(-50L);

    // Replaying the whole edit returns the same entry and changes nothing.
    List<MemberBalance> after = balancesOf(g);
    assertThat(journal.replace(g, original, next, "e:edit"))
        .isEqualTo(new Posted(edited.entryId(), true));
    assertThat(entries(g)).isEqualTo(3);
    assertThat(balancesOf(g)).isEqualTo(after);
  }

  @Test
  void anEditOfAReversedOrNonMemberEntryIsRefusedWhole() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID g = ids.get(0), a = ids.get(1), b = ids.get(2);
    UUID s =
        journal
            .post(JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 9L), "s")
            .entryId();
    journal.reverse(g, s, Seed.caller(dsl, g), "del");
    JournalEntry next = JournalEntry.settlement(g, Seed.caller(dsl, g), "INR", a, b, 10L);

    assertConflict(() -> journal.replace(g, s, next, "edit"), Reason.ALREADY_REVERSED);
    UUID writeOff =
        journal.post(
            new JournalEntry(
                g,
                EntryKind.WRITE_OFF,
                null,
                List.of(new JournalLine(a, "INR", -1L), new JournalLine(b, "INR", 1L))));
    assertConflict(() -> journal.replace(g, writeOff, next, "edit2"), Reason.NOT_REVERSIBLE);
    assertThat(entries(g)).isEqualTo(3);
  }
}
