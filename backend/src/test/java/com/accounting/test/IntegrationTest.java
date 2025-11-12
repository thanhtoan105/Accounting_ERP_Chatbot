package com.accounting.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
  }
}
