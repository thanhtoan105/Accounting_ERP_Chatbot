package com.accounting.repository.dashboard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.dashboard.WidgetConfiguration;

@Repository
public interface WidgetConfigurationRepository extends JpaRepository<WidgetConfiguration, UUID> {

    List<WidgetConfiguration> findByCompanyIdOrderByDisplayOrderAsc(Long companyId);

    List<WidgetConfiguration> findByCompanyIdAndIsEnabledTrueOrderByDisplayOrderAsc(Long companyId);

    Optional<WidgetConfiguration> findByCompanyIdAndWidgetCode(Long companyId, String widgetCode);

    @Query("SELECT w FROM WidgetConfiguration w WHERE w.companyId = :companyId AND w.isEnabled = true ORDER BY w.displayOrder")
    List<WidgetConfiguration> findEnabledByCompanyId(@Param("companyId") Long companyId);
    
    /**
     * Find widgets accessible by role. Since requiredRoles is stored as JSON,
     * filtering by role is done in the service layer.
     */
    default List<WidgetConfiguration> findAccessibleByRole(Long companyId, String role) {
        return findEnabledByCompanyId(companyId).stream()
                .filter(w -> w.isAccessibleByRole(role))
                .toList();
    }

    boolean existsByCompanyId(Long companyId);
}
