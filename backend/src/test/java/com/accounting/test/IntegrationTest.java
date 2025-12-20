package com.accounting.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using Testcontainers with TimescaleDB and Redis.
 * Uses a separate Flyway DataSource to avoid HikariCP connection pool exhaustion.
 */
@Testcontainers(disabledWithoutDocker = true)
@Import({TestStorageConfig.class, TestFlywayConfig.class})
public abstract class IntegrationTest {

  private static final DockerImageName TIMESCALEDB_IMAGE = DockerImageName
      .parse("timescale/timescaledb:latest-pg16")
      .asCompatibleSubstituteFor("postgres");

  @Container
  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TIMESCALEDB_IMAGE)
      .withDatabaseName("accounting_test")
      .withUsername("test")
      .withPassword("test")
      .withReuse(false);

  @Container
  @SuppressWarnings("resource")
  protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379)
      .withReuse(false);

  @Autowired
  private Flyway flyway;

  // Track if database has been initialized for this container instance
  private static volatile boolean initialized = false;
  private static volatile String lastContainerId = null;

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.clean-disabled", () -> false);
    registry.add("spring.flyway.out-of-order", () -> true);
    registry.add("spring.flyway.validate-on-migrate", () -> false);
    
    // Redis configuration
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @BeforeEach
  void setupDatabase() {
    // Only clean and migrate once per container instance
    // This avoids connection pool exhaustion while still ensuring clean state
    String currentContainerId = POSTGRES.getContainerId();
    
    synchronized (IntegrationTest.class) {
      if (!initialized || !currentContainerId.equals(lastContainerId)) {
        try {
          flyway.clean();
        } catch (Exception e) {
          System.err.println("Warning: Flyway clean failed: " + e.getMessage());
        }
        flyway.migrate();
        initialized = true;
        lastContainerId = currentContainerId;
      }
    }
  }
}
