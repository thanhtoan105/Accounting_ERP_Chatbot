package com.accounting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.DefaultAccountEntry;

public interface DefaultAccountEntryRepository extends JpaRepository<DefaultAccountEntry, Long> {

  List<DefaultAccountEntry> findByDefaultAccountIdOrderByOrderingPosition(Long defaultAccountId);

  void deleteByDefaultAccountId(Long defaultAccountId);
}
