package com.accounting.service.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Year;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Utility class for generating unique supplier codes in format SUP-YYYY-NNNN.
 * Thread-safe implementation using database function for atomic sequence
 * generation.
 */
@Component
public class SupplierCodeGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Generate next supplier code for a company.
     * Format: SUP-YYYY-NNNN (e.g., SUP-2025-0001)
     * Thread-safe and handles year rollover automatically.
     *
     * @param companyId company ID
     * @return generated supplier code
     */
    @Transactional
    public String generateCode(Long companyId) {
        int currentYear = Year.now().getValue();

        // Call database function for thread-safe code generation
        Query query = entityManager.createNativeQuery(
                "SELECT get_next_supplier_code(:companyId, :year)");
        query.setParameter("companyId", companyId);
        query.setParameter("year", currentYear);

        String code = (String) query.getSingleResult();
        return code;
    }
}

