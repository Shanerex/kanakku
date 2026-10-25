package dev.kanakku;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  static final ApplicationModules MODULES = ApplicationModules.of(KanakkuApplication.class);

  @Test
  void verifiesModuleStructure() {
    MODULES.verify();
  }

  @Test
  void ledgerDependsOnlyOnPlatformAndTheGeneratedSqlClasses() {
    assertThat(dependenciesOf("ledger")).isSubsetOf("platform", "jooq");
  }

  @Test
  void groupsDependsOnlyOnIdentityLedgerPlatformAndTheGeneratedSqlClasses() {
    assertThat(dependenciesOf("groups")).isSubsetOf("identity", "ledger", "platform", "jooq");
  }

  @Test
  void identityDoesNotDependOnGroupsOrLedger() {
    assertThat(dependenciesOf("identity")).doesNotContain("groups", "ledger");
  }

  private static Set<String> dependenciesOf(String module) {
    var target = MODULES.getModuleByName(module).orElseThrow();
    return target.getDirectDependencies(MODULES).stream()
        .map(d -> d.getTargetModule().getIdentifier().toString())
        .collect(Collectors.toSet());
  }
}
