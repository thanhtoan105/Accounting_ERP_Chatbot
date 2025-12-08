package com.accounting.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accounting.entity.ARStatementDelivery;

/**
 * Repository for ARStatementDelivery entity with company-scoped queries.
 */
@Repository
public interface ARStatementDeliveryRepository
    extends JpaRepository<ARStatementDelivery, UUID>,
        JpaSpecificationExecutor<ARStatementDelivery> {

  /**
   * Find delivery by ID with company scope.
   */
  @Query(
      "SELECT d FROM ARStatementDelivery d WHERE d.id = :id AND d.companyId = :companyId")
  Optional<ARStatementDelivery> findByIdAndCompanyId(
      @Param("id") UUID id, @Param("companyId") Long companyId);

  /**
   * Find all deliveries for a statement with company scope.
   */
  @Query(
      "SELECT d FROM ARStatementDelivery d WHERE d.statementId = :statementId AND d.companyId = :companyId ORDER BY d.sentAt DESC")
  List<ARStatementDelivery> findByStatementIdAndCompanyId(
      @Param("statementId") UUID statementId, @Param("companyId") Long companyId);

  /**
   * Find all deliveries for a customer with company scope.
   */
  @Query(
      "SELECT d FROM ARStatementDelivery d WHERE d.customerId = :customerId AND d.companyId = :companyId ORDER BY d.sentAt DESC")
  List<ARStatementDelivery> findByCustomerIdAndCompanyId(
      @Param("customerId") Long customerId, @Param("companyId") Long companyId);
}
