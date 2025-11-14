package com.accounting.repository;

import com.accounting.entity.DefaultAccountEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefaultAccountEntryRepository extends JpaRepository<DefaultAccountEntry, Long> {

  List<DefaultAccountEntry> findByDefaultAccountIdOrderByOrderingPosition(Long defaultAccountId);

  void deleteByDefaultAccountId(Long defaultAccountId);
}

