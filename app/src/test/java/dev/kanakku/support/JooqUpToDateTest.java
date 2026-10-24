package dev.kanakku.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JooqUpToDateTest {

  private static final Path COMMITTED = Path.of("src/main/java/dev/kanakku/jooq");

  /** The only hand-written file in the generated folder: it marks the package as an open module. */
  private static final String HAND_WRITTEN = "package-info.java";

  @TempDir Path temp;

  @Test
  void committedClassesMatchTheMigrations() throws Exception {
    JooqGenerator.generateInto(temp);
    Path generated = temp.resolve(JooqGenerator.PACKAGE.replace('.', '/'));

    if (Boolean.getBoolean("jooq.regenerate")) {
      for (String file : listFiles(COMMITTED)) {
        Files.delete(COMMITTED.resolve(file));
      }
      copyTree(generated, COMMITTED);
    }

    assertThat(listFiles(COMMITTED)).isEqualTo(listFiles(generated));
    for (String file : listFiles(generated)) {
      assertThat(Files.readString(COMMITTED.resolve(file)))
          .as("generated file %s", file)
          .isEqualTo(Files.readString(generated.resolve(file)));
    }
  }

  private static java.util.List<String> listFiles(Path root) throws IOException {
    if (!Files.exists(root)) {
      return java.util.List.of();
    }
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile)
          .filter(p -> !p.getFileName().toString().equals(HAND_WRITTEN))
          .map(p -> root.relativize(p).toString())
          .sorted()
          .toList();
    }
  }

  private static void copyTree(Path from, Path to) throws IOException {
    try (Stream<Path> walk = Files.walk(from)) {
      for (Path p : walk.toList()) {
        Path target = to.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p)) {
          Files.createDirectories(target);
        } else {
          Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }
}
