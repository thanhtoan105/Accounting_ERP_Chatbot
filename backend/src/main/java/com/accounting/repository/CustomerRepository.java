package com.accounting.repository;

import com.accounting.entity.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for Customer entities with company scoping and search capabilities.
 */
public interface CustomerRepository
    extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

  /**
   * Find all customers for a company.
   *
   * @param companyId company ID
   * @return list of customers
   */
  List<Customer> findByCompanyId(Long companyId);

  /**
   * Find customer by company and ID.
   *
   * @param companyId company ID
   * @param id customer ID
   * @return customer or empty
   */
  Optional<Customer> findByCompanyIdAndId(Long companyId, Long id);

  /**
   * Check if customer with tax code exists for company (for duplicate validation).
   *
   * @param companyId company ID
   * @param taxCode tax code
   * @param excludeId customer ID to exclude (for updates)
   * @return true if tax code exists, false otherwise
   */
  @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.companyId = :companyId AND c.taxCode = :taxCode AND (:excludeId IS NULL OR c.id != :excludeId)")
  boolean existsByCompanyIdAndTaxCode(
      @Param("companyId") Long companyId, @Param("taxCode") String taxCode, @Param("excludeId") Long excludeId);

  /**
   * Check if customer with email exists for company (for duplicate validation).
   *
   * @param companyId company ID
   * @param email email address
   * @param excludeId customer ID to exclude (for updates)
   * @return true if email exists, false otherwise
   */
  @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.companyId = :companyId AND c.email = :email AND c.email IS NOT NULL AND (:excludeId IS NULL OR c.id != :excludeId)")
  boolean existsByCompanyIdAndEmail(
      @Param("companyId") Long companyId, @Param("email") String email, @Param("excludeId") Long excludeId);

  /**
   * Check if customer with phone exists for company (for duplicate validation).
   *
   * @param companyId company ID
   * @param phone phone number
   * @param excludeId customer ID to exclude (for updates)
   * @return true if phone exists, false otherwise
   */
  @Query("SELECT COUNT(c) > 0 FROM Customer c WHERE c.companyId = :companyId AND c.phone = :phone AND c.phone IS NOT NULL AND (:excludeId IS NULL OR c.id != :excludeId)")
  boolean existsByCompanyIdAndPhone(
      @Param("companyId") Long companyId, @Param("phone") String phone, @Param("excludeId") Long excludeId);

  /**
   * Find duplicate customer by tax code, email, or phone.
   *
   * @param companyId company ID
   * @param taxCode tax code (optional)
   * @param email email (optional)
   * @param phone phone (optional)
   * @return first matching customer or empty
   */
  @Query("SELECT c FROM Customer c WHERE c.companyId = :companyId AND "
      + "((:taxCode IS NOT NULL AND c.taxCode = :taxCode) OR "
      + "(:email IS NOT NULL AND c.email = :email) OR "
      + "(:phone IS NOT NULL AND c.phone = :phone))")
  Optional<Customer> findDuplicate(
      @Param("companyId") Long companyId,
      @Param("taxCode") String taxCode,
      @Param("email") String email,
      @Param("phone") String phone);

  /**
   * Search customers by code or name using native PostgreSQL unaccent function.
   * Supports unaccented Vietnamese search (e.g., "nha" matches "nhà").
   *
   * @param companyId company ID
   * @param searchTerm search term (matches code or unaccented name)
   * @return list of matching customers
   */
  @Query(value = "SELECT * FROM customers c "
      + "WHERE c.company_id = :companyId "
      + "AND ("
      + "  c.code ILIKE '%' || :searchTerm || '%' "
      + "  OR unaccent_search(c.name) ILIKE '%' || unaccent_search(:searchTerm) || '%' "
      + ") "
      + "ORDER BY c.name", nativeQuery = true)
  List<Customer> searchByCodeOrNameNative(
      @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);
}


