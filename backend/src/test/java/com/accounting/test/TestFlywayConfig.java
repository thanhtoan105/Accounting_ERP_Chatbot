package com.accounting.test;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Test configuration that provides a separate non-pooled DataSource for Flyway.
 * This prevents HikariCP connection pool exhaustion when running flyway.clean()
 * in integration tests with TimescaleDB.
 */
@TestConfiguration
public class TestFlywayConfig {

    /**
     * Create a separate non-pooled DataSource for Flyway operations.
     * This bypasses HikariCP and prevents connection pool exhaustion
     * when running flyway.clean() which can leak connections on TimescaleDB.
     */
    @Bean
    @FlywayDataSource
    public DataSource flywayDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {

        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(org.postgresql.Driver.class);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
