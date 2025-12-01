package com.accounting.repository;

import com.accounting.entity.ChatbotQuery;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for ChatbotQuery entity.
 * Provides custom query methods for retrieving chatbot interactions
 * with automatic company-scoped filtering via CompanyScopedEntity.
 */
@Repository
public interface ChatbotQueryRepository extends JpaRepository<ChatbotQuery, Long> {

    /**
     * Find all queries by session ID, ordered by creation time ascending.
     * Used to retrieve conversation history for a specific session.
     *
     * @param sessionId The session identifier
     * @param companyId The company ID for scoping
     * @return List of queries in chronological order
     */
    List<ChatbotQuery> findBySessionIdAndCompanyIdOrderByCreatedAtAsc(
            String sessionId, Long companyId);

    /**
     * Find all queries by user ID with pagination, ordered by creation time descending.
     * Used for user-specific query history and analytics.
     *
     * @param userId The user ID
     * @param companyId The company ID for scoping
     * @param pageable Pagination parameters
     * @return Page of queries in reverse chronological order
     */
    Page<ChatbotQuery> findByUserIdAndCompanyIdOrderByCreatedAtDesc(
            Long userId, Long companyId, Pageable pageable);

    /**
     * Find all queries for a company with pagination, ordered by creation time descending.
     * Used for company-wide analytics and monitoring.
     *
     * @param companyId The company ID
     * @param pageable Pagination parameters
     * @return Page of queries in reverse chronological order
     */
    Page<ChatbotQuery> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    /**
     * Find queries within a date range for a company.
     * Used for time-based analytics and reporting.
     *
     * @param companyId The company ID
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param pageable Pagination parameters
     * @return Page of queries within the date range
     */
    Page<ChatbotQuery> findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long companyId, Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Find queries by user within a date range.
     * Used for user-specific time-based analytics.
     *
     * @param userId The user ID
     * @param companyId The company ID for scoping
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param pageable Pagination parameters
     * @return Page of queries within the date range
     */
    Page<ChatbotQuery> findByUserIdAndCompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long userId, Long companyId, Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Find queries with low confidence scores for quality monitoring.
     * Used to identify queries that may need human review or model tuning.
     *
     * @param companyId The company ID
     * @param threshold Maximum confidence score threshold (e.g., 0.5)
     * @param pageable Pagination parameters
     * @return Page of low-confidence queries
     */
    Page<ChatbotQuery> findByCompanyIdAndConfidenceScoreLessThanOrderByCreatedAtDesc(
            Long companyId, Float threshold, Pageable pageable);

    /**
     * Count total queries for a company.
     * Used for usage metrics and billing.
     *
     * @param companyId The company ID
     * @return Total query count
     */
    long countByCompanyId(Long companyId);

    /**
     * Count queries for a user.
     * Used for user-specific usage metrics.
     *
     * @param userId The user ID
     * @param companyId The company ID for scoping
     * @return User's query count
     */
    long countByUserIdAndCompanyId(Long userId, Long companyId);

    /**
     * Count queries within a date range for a company.
     * Used for time-based analytics and trend analysis.
     *
     * @param companyId The company ID
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return Query count within date range
     */
    long countByCompanyIdAndCreatedAtBetween(Long companyId, Instant startDate, Instant endDate);

    /**
     * Calculate average confidence score for a company.
     * Used for model quality monitoring.
     *
     * @param companyId The company ID
     * @return Average confidence score, or null if no queries exist
     */
    @Query(
            "SELECT AVG(c.confidenceScore) FROM ChatbotQuery c WHERE c.companyId = :companyId AND c.confidenceScore IS NOT NULL")
    Float calculateAverageConfidenceScore(@Param("companyId") Long companyId);

    /**
     * Calculate average response time for a company.
     * Used for performance monitoring.
     *
     * @param companyId The company ID
     * @return Average response time in milliseconds, or null if no queries exist
     */
    @Query(
            "SELECT AVG(c.responseTimeMs) FROM ChatbotQuery c WHERE c.companyId = :companyId AND c.responseTimeMs IS NOT NULL")
    Float calculateAverageResponseTime(@Param("companyId") Long companyId);

    /**
     * Find most recent query in a session.
     * Used for session state management.
     *
     * @param sessionId The session identifier
     * @param companyId The company ID for scoping
     * @return The most recent query, or null if session doesn't exist
     */
    ChatbotQuery findFirstBySessionIdAndCompanyIdOrderByCreatedAtDesc(
            String sessionId, Long companyId);
}
