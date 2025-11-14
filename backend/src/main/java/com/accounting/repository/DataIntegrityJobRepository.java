package com.accounting.repository;

import com.accounting.entity.DataIntegrityJob;
import com.accounting.entity.DataIntegrityJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataIntegrityJobRepository extends JpaRepository<DataIntegrityJob, UUID> {

  Optional<DataIntegrityJob> findTopByCompanyIdOrderByStartedAtDesc(Long companyId);

  long countByCompanyIdAndStatusAndStartedAtAfter(
      Long companyId, DataIntegrityJobStatus status, Instant threshold);

  List<DataIntegrityJob> findByCompanyId(Long companyId);
}
