package com.accounting.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Import(TestStorageConfig.class)
public abstract class IntegrationTest {
  // QUAN TRỌNG: Static final để container được share giữa tất cả test classes
  // withReuse(true) cho phép container được reuse giữa các test runs
  @SuppressWarnings("resource") // Container được reuse intentionally, cleanup khi JVM shutdown
  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
      DockerImageName.parse("postgres:16.4-alpine"))
      .withDatabaseName("accounting_test")
      .withUsername("test")
      .withPassword("test")
      .withReuse(true); // <-- QUAN TRỌNG: Cho phép reuse container

  @org.springframework.beans.factory.annotation.Autowired private Flyway flyway;

  @BeforeAll
  static void startContainer() {
    // Container chỉ khởi động 1 lần cho tất cả tests
    // Nếu container đã chạy (từ test class trước), sẽ reuse
    if (!POSTGRES.isRunning()) {
    POSTGRES.start();
    }
  }

  @AfterAll
  static void stopContainer() {
    // KHÔNG stop container để có thể reuse cho test classes tiếp theo
    // Container sẽ tự động stop khi JVM shutdown hoặc khi không còn test nào dùng
    // Chỉ stop nếu thực sự cần (ví dụ: cleanup cuối cùng)
    // POSTGRES.stop(); // <-- Commented out để enable reuse
    
    // Note: Suppressing resource leak warning vì container được reuse intentionally
    // Container sẽ được cleanup khi JVM shutdown
  }

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.flyway.enabled", () -> true);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    registry.add("spring.flyway.clean-disabled", () -> false);
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
