package com.example.feniksdemo;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

public class FlywayCommand {

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("migrate") || args[0].equals("info") || args[0].equals("reset"))) {
            throw new IllegalArgumentException("Expected migrate, info or reset");
        }

        SpringApplication application = new SpringApplication(FeniksDemoApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        // CLI operations control migration explicitly; info must not apply pending migrations.
        application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "commandMigrationStrategy", (FlywayMigrationStrategy) flyway -> { }));

        try (var context = application.run()) {
            Flyway flyway = context.getBean(Flyway.class);
            if (args[0].equals("reset")) {
                DemoDatabaseReset.reset(flyway, context.getEnvironment().getRequiredProperty("spring.datasource.url"),
                        java.nio.file.Path.of(""));
            } else if (args[0].equals("migrate")) {
                flyway.migrate();
            }
            for (var migration : flyway.info().all()) {
                System.out.printf("%s | %s | %s%n",
                        migration.getVersion(), migration.getDescription(), migration.getState());
            }
        }
    }
}
