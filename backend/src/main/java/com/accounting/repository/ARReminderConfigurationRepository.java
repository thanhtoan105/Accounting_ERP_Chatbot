package com.accounting.repository;

import com.accounting.entity.ARReminderConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for ARReminderConfiguration entity.
 */
@Repository
public interface ARReminderConfigurationRepository
        extends JpaRepository<ARReminderConfiguration, UUID> {

    /**
     * Find reminder configuration by company ID.
     *
     * @param companyId company ID
     * @return optional reminder configuration
     */
    Optional<ARReminderConfiguration> findByCompanyId(Long companyId);
}
