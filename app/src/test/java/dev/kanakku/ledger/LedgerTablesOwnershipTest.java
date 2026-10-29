package dev.kanakku.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Only the ledger module touches the journal and balance tables; others go through its api. */
class LedgerTablesOwnershipTest {

  private static final Path MAIN = Path.of("src/main/java/dev/kanakku");
  private static final Pattern LEDGER_TABLES =
      Pattern.compile("(?i)journal_entries|journal_lines|member_balances");

  @Test
  void noModuleOutsideTheLedgerReferencesItsTables() throws IOException {
    List<String> offenders;
    try (Stream<Path> files = Files.walk(MAIN)) {
      offenders =
          files
              .filter(p -> p.toString().endsWith(".java"))
              .filter(p -> !p.startsWith(MAIN.resolve("ledger")))
              // The generated jOOQ classes describe every table; they belong to no module.
              .filter(p -> !p.startsWith(MAIN.resolve("jooq")))
              .filter(LedgerTablesOwnershipTest::mentionsLedgerTables)
              .map(Path::toString)
              .toList();
    }
    assertThat(offenders).isEmpty();
  }

  @Test
  void theCheckSeesTheLedgerItself() {
    assertThat(mentionsLedgerTables(MAIN.resolve("ledger/repository/JournalRepository.java")))
        .isTrue();
  }

  private static boolean mentionsLedgerTables(Path file) {
    try {
      return LEDGER_TABLES.matcher(Files.readString(file)).find();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
