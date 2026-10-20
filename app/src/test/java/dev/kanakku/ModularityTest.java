package dev.kanakku;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(KanakkuApplication.class).verify();
  }
}
