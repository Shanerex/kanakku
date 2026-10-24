package dev.kanakku.support;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Applies every Flyway migration to a throwaway Postgres and generates the jOOQ classes from it.
 * {@code JooqUpToDateTest} runs this into a temp folder and fails when the committed classes in
 * {@code app/src/main/java/dev/kanakku/jooq} differ; run it with {@code -Djooq.regenerate=true} to
 * rewrite them.
 */
public final class JooqGenerator {

  public static final String PACKAGE = "dev.kanakku.jooq";

  private JooqGenerator() {}

  public static void generateInto(Path sourceRoot) throws Exception {
    try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")) {
      postgres.start();
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .load()
          .migrate();
      Configuration configuration =
          new Configuration()
              .withJdbc(
                  new Jdbc()
                      .withDriver("org.postgresql.Driver")
                      .withUrl(postgres.getJdbcUrl())
                      .withUser(postgres.getUsername())
                      .withPassword(postgres.getPassword()))
              .withGenerator(
                  new Generator()
                      .withDatabase(
                          new Database()
                              .withName("org.jooq.meta.postgres.PostgresDatabase")
                              .withInputSchema("public")
                              .withExcludes("flyway_schema_history"))
                      .withGenerate(
                          new Generate()
                              .withGeneratedAnnotation(false)
                              .withDeprecated(false)
                              .withRelations(true)
                              .withPojos(false)
                              .withDaos(false)
                              .withJavaTimeTypes(true))
                      .withTarget(
                          new Target()
                              .withPackageName(PACKAGE)
                              .withDirectory(sourceRoot.toString())));
      GenerationTool.generate(configuration);
    }
  }
}
