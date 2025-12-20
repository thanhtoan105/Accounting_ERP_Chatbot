package com.accounting.repository.report;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.report.ReportSchedule;

@Repository
public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

  Optional<ReportSchedule> findByIdAndCompanyId(UUID id, Long companyId);

  Page<ReportSchedule> findByCompanyId(Long companyId, Pageable pageable);

  List<ReportSchedule> findByCompanyIdAndIsActiveTrue(Long companyId);

  @Query("SELECT s FROM ReportSchedule s WHERE s.companyId = :companyId AND s.isActive = true AND s.nextRunAt <= :now")
  List<ReportSchedule> findDueSchedules(@Param("companyId") Long companyId, @Param("now") Instant now);

  boolean existsByCompanyIdAndName(Long companyId, String name);

  List<ReportSchedule> findByCompanyIdAndOwnerId(Long companyId, Long ownerId);

  @Query("SELECT s FROM ReportSchedule s WHERE s.companyId = :companyId AND s.isActive = true AND s.owner.status != 'ACTIVE'")
  List<ReportSchedule> findOrphanedSchedules(@Param("companyId") Long companyId);
}
