package com.accounting.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.DataIntegrityFinding;
import com.accounting.entity.DataIntegrityJob;

public interface DataIntegrityFindingRepository extends JpaRepository<DataIntegrityFinding, UUID> {

  List<DataIntegrityFinding> findByJob(DataIntegrityJob job);
}
