package dev.kanakku.ledger.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Currency;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The web client formats amounts with {@code web/src/features/groups/minorUnits.json}, so it must
 * list exactly the currencies whose minor-unit digits the server (the JDK) does not count as 2.
 */
class MinorUnitsTableTest {

  private static final Path TABLE = Path.of("../web/src/features/groups/minorUnits.json");

  @Test
  void theWebTableMatchesTheJdksIso4217Digits() throws Exception {
    assumeTrue(Files.exists(TABLE), "web sources are not next to the app module");
    Map<String, Integer> read =
        JsonMapper.builder().build().readValue(TABLE.toFile(), new TypeReference<>() {});
    Map<String, Integer> web = new TreeMap<>(read);

    Map<String, Integer> jdk = new TreeMap<>();
    for (Currency currency : Currency.getAvailableCurrencies()) {
      int digits = currency.getDefaultFractionDigits();
      if (digits >= 0 && digits != 2) {
        jdk.put(currency.getCurrencyCode(), digits);
      }
    }

    assertThat(web).isEqualTo(jdk);
  }
}
