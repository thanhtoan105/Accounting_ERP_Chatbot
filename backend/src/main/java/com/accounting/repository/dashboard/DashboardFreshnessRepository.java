package com.accounting.repository.dashboard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.dashboard.DashboardFreshness;
import com.accounting.entity.dashboard.FreshnessLevel;

@Repository
public interface DashboardFreshnessRepository extends JpaRepository<DashboardFreshness, UUID> {

    Optional<DashboardFreshness> findByCompanyId(Long companyId);

    @Query("SELECT f FROM DashboardFreshness f WHERE f.freshnessLevel = :level")
    List<DashboardFreshness> findByFreshnessLevel(@Param("level") FreshnessLevel level);

    @Query("SELECT f FROM DashboardFreshness f WHERE f.consecutiveFailures >= :threshold")
    List<DashboardFreshness> findWithConsecutiveFailures(@Param("threshold") Integer threshold);

    @Query("SELECT f.freshnessLevel FROM DashboardFreshness f WHERE f.companyId = :companyId")
    Optional<FreshnessLevel> getFreshnessLevelByCompanyId(@Param("companyId") Long companyId);
}
