package com.accounting.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@Import(TestStorageConfig.class)
public abstract class IntegrationTest {
  // Container managed by @Testcontainers extension - starts before @DynamicPropertySource
  // Mark TimescaleDB as compatible substitute for PostgreSQL
  private static final DockerImageName TIMESCALEDB_IMAGE = DockerImageName
      .parse("timescale/timescaledb:latest-pg16")
      .asCompatibleSubstituteFor("postgres");

  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TIMESCALEDB_IMAGE)
      .withDatabaseName("accounting_test")
      .withUsername("test")
      .withPassword("test")
      .withReuse(true);

  @org.springframework.beans.factory.annotation.Autowired private Flyway flyway;

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.clean-disabled", () -> false);
    registry.add("spring.flyway.out-of-order", () -> true); // Allow out-of-order migrations in tests
    registry.add("spring.flyway.validate-on-migrate", () -> false); // Disable validation on migrate
  }

  @BeforeEach
  void resetDatabase() {
    try {
      // Check if schema history exists before attempting clean
      // This prevents errors when database is completely empty
      try {
        flyway.info(); // This will fail if schema history doesn't exist
        flyway.clean();
      } catch (org.flywaydb.core.internal.exception.FlywaySqlException e) {
        // If schema history table doesn't exist, skip clean and just migrate
        // This is expected for a fresh database
        if (e.getMessage() != null && e.getMessage().contains("flyway_schema_history")) {
          // Schema history doesn't exist, skip clean
        } else {
          throw e; // Re-throw if it's a different error
        }
      }
    } catch (Exception e) {
      // If clean fails for any other reason, log and continue with migrate
      // This can happen if database is in an inconsistent state
      System.err.println("Warning: Flyway clean failed, continuing with migrate: " + e.getMessage());
    }
    flyway.migrate();
  }
}
