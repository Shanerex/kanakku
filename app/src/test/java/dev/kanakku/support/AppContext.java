package dev.kanakku.support;

import dev.kanakku.KanakkuApplication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One application context for jqwik properties, which cannot use {@code @SpringBootTest}. It shares
 * the test Postgres with the rest of the suite, so properties must use data of their own.
 */
public final class AppContext {

  private static ConfigurableApplicationContext context;

  private AppContext() {}

  public static synchronized ConfigurableApplicationContext get() {
    if (context == null) {
      // Command-line arguments beat application.yml; builder properties would lose to it.
      List<String> args =
          new ArrayList<>(List.of("--server.port=0", "--db-scheduler.enabled=false"));
      PostgresIntegrationTest.datasourceProperties()
          .forEach((key, value) -> args.add("--" + key + "=" + value));
      context =
          new SpringApplicationBuilder(KanakkuApplication.class).run(args.toArray(String[]::new));
    }
    return context;
  }

  public static <T> T bean(Class<T> type) {
    return get().getBean(type);
  }
}
