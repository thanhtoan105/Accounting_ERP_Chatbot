package com.accounting.repository;

import com.accounting.entity.Customer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository
    extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
  List<Customer> findByCompanyId(Long companyId);
}


