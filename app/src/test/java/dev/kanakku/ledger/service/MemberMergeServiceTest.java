package dev.kanakku.ledger.service;

import static dev.kanakku.jooq.Tables.JOURNAL_ENTRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kanakku.ledger.api.Balances;
import dev.kanakku.ledger.api.EntryKind;
import dev.kanakku.ledger.api.Journal;
import dev.kanakku.ledger.api.JournalEntry;
import dev.kanakku.ledger.api.JournalLine;
import dev.kanakku.ledger.api.MemberMerges;
import dev.kanakku.ledger.api.MoneyBalance;
import dev.kanakku.support.PostgresIntegrationTest;
import dev.kanakku.support.Seed;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MemberMergeServiceTest extends PostgresIntegrationTest {

  @Autowired Journal journal;
  @Autowired Balances balances;
  @Autowired MemberMerges merges;
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
  void aGuestsBalanceMovesOntoTheMemberAndTheHistoryNamesAMerge() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), guest = ids.get(1), member = ids.get(2), other = ids.get(3);
    owe(g, other, guest, "INR", 700);
    owe(g, member, other, "INR", 200);
    UUID actor = Seed.user(dsl, "Actor");

    List<MoneyBalance> moved = merges.merge(g, guest, member, actor);

    assertThat(moved).containsExactly(new MoneyBalance("INR", -700));
    UUID caller = Seed.caller(dsl, g);
    assertThat(balances.ofMember(g, guest, caller)).containsExactly(new MoneyBalance("INR", 0));
    assertThat(balances.ofMember(g, member, caller)).containsExactly(new MoneyBalance("INR", -500));
    var entry =
        dsl.selectFrom(JOURNAL_ENTRIES)
            .where(JOURNAL_ENTRIES.GROUP_ID.eq(g))
            .and(JOURNAL_ENTRIES.KIND.eq("MERGE"))
            .fetchSingle();
    assertThat(entry.getActorUserId()).isEqualTo(actor);
  }

  @Test
  void eachCurrencyGetsItsOwnEntry() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 3);
    UUID g = ids.get(0), guest = ids.get(1), member = ids.get(2), other = ids.get(3);
    owe(g, guest, other, "INR", 100);
    owe(g, other, guest, "USD", 5);

    List<MoneyBalance> moved = merges.merge(g, guest, member, null);

    assertThat(moved)
        .containsExactlyInAnyOrder(new MoneyBalance("INR", 100), new MoneyBalance("USD", -5));
    assertThat(
            dsl.fetchCount(
                JOURNAL_ENTRIES,
                JOURNAL_ENTRIES.GROUP_ID.eq(g).and(JOURNAL_ENTRIES.KIND.eq("MERGE"))))
        .isEqualTo(2);
  }

  @Test
  void mergingAMemberIntoThemselfIsRefused() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);

    assertThatThrownBy(() -> merges.merge(ids.get(0), ids.get(1), ids.get(1), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void membersOfAnotherGroupCannotBeMerged() {
    List<UUID> a = Seed.groupWithGuests(dsl, 2);
    List<UUID> b = Seed.groupWithGuests(dsl, 2);
    owe(a.get(0), a.get(1), a.get(2), "INR", 100);

    assertThatThrownBy(() -> merges.merge(a.get(0), a.get(2), b.get(1), null))
        .isInstanceOf(Exception.class);

    // Nothing was posted: the all-or-nothing transaction rolled the entry back.
    UUID caller = Seed.caller(dsl, a.get(0));
    assertThat(balances.ofMember(a.get(0), a.get(2), caller))
        .containsExactly(new MoneyBalance("INR", -100));
  }

  @Test
  void theMergeKindIsAcceptedByTheJournalButAnUnknownOneIsNot() {
    List<UUID> ids = Seed.groupWithGuests(dsl, 2);
    UUID entry = UUID.randomUUID();

    dsl.insertInto(JOURNAL_ENTRIES)
        .set(JOURNAL_ENTRIES.ID, entry)
        .set(JOURNAL_ENTRIES.GROUP_ID, ids.get(0))
        .set(JOURNAL_ENTRIES.KIND, "MERGE")
        .execute();

    assertThatThrownBy(
            () ->
                dsl.insertInto(JOURNAL_ENTRIES)
                    .set(JOURNAL_ENTRIES.ID, UUID.randomUUID())
                    .set(JOURNAL_ENTRIES.GROUP_ID, ids.get(0))
                    .set(JOURNAL_ENTRIES.KIND, "SOMETHING")
                    .execute())
        .isInstanceOf(Exception.class);
  }
}
