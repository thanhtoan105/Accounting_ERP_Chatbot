package com.accounting.service.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Year;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utility class for generating unique customer codes in format CUST-YYYY-NNNN.
 * Thread-safe implementation using database function for atomic sequence
 * generation.
 */
@Component
public class CustomerCodeGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Generate next customer code for a company.
     * Format: CUST-YYYY-NNNN (e.g., CUST-2025-0001)
     * Thread-safe and handles year rollover automatically.
     *
     * @param companyId company ID
     * @return generated customer code
     */
    @Transactional
    public String generateCode(Long companyId) {
        int currentYear = Year.now().getValue();

        // Call database function for thread-safe code generation
        Query query = entityManager.createNativeQuery(
                "SELECT get_next_customer_code(:companyId, :year)");
        query.setParameter("companyId", companyId);
        query.setParameter("year", currentYear);

        String code = (String) query.getSingleResult();
        return code;
    }
}
