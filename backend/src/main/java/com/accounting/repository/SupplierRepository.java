package com.accounting.repository;

import com.accounting.entity.Supplier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for Supplier entities with company scoping and search capabilities.
 */
public interface SupplierRepository
    extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

  /**
   * Find all suppliers for a company.
   *
   * @param companyId company ID
   * @return list of suppliers
   */
  List<Supplier> findByCompanyId(Long companyId);

  /**
   * Find supplier by company and ID.
   *
   * @param companyId company ID
   * @param id supplier ID
   * @return supplier or empty
   */
  Optional<Supplier> findByCompanyIdAndId(Long companyId, Long id);

  /**
   * Check if supplier with tax code exists for company (for duplicate validation).
   *
   * @param companyId company ID
   * @param taxCode tax code
   * @param excludeId supplier ID to exclude (for updates)
   * @return true if tax code exists, false otherwise
   */
  @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE s.companyId = :companyId AND s.taxCode = :taxCode AND (:excludeId IS NULL OR s.id != :excludeId)")
  boolean existsByCompanyIdAndTaxCode(
      @Param("companyId") Long companyId, @Param("taxCode") String taxCode, @Param("excludeId") Long excludeId);

  /**
   * Check if supplier with email exists for company (for duplicate validation).
   *
   * @param companyId company ID
   * @param email email address
   * @param excludeId supplier ID to exclude (for updates)
   * @return true if email exists, false otherwise
   */
  @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE s.companyId = :companyId AND s.email = :email AND s.email IS NOT NULL AND (:excludeId IS NULL OR s.id != :excludeId)")
  boolean existsByCompanyIdAndEmail(
      @Param("companyId") Long companyId, @Param("email") String email, @Param("excludeId") Long excludeId);

  /**
   * Check if supplier with phone exists for company (for duplicate validation).
   *
   * @param companyId company ID
   * @param phone phone number
   * @param excludeId supplier ID to exclude (for updates)
   * @return true if phone exists, false otherwise
   */
  @Query("SELECT COUNT(s) > 0 FROM Supplier s WHERE s.companyId = :companyId AND s.phone = :phone AND s.phone IS NOT NULL AND (:excludeId IS NULL OR s.id != :excludeId)")
  boolean existsByCompanyIdAndPhone(
      @Param("companyId") Long companyId, @Param("phone") String phone, @Param("excludeId") Long excludeId);

  /**
   * Find duplicate supplier by tax code, email, or phone.
   *
   * @param companyId company ID
   * @param taxCode tax code (optional)
   * @param email email (optional)
   * @param phone phone (optional)
   * @return first matching supplier or empty
   */
  @Query("SELECT s FROM Supplier s WHERE s.companyId = :companyId AND "
      + "((:taxCode IS NOT NULL AND s.taxCode = :taxCode) OR "
      + "(:email IS NOT NULL AND s.email = :email) OR "
      + "(:phone IS NOT NULL AND s.phone = :phone))")
  Optional<Supplier> findDuplicate(
      @Param("companyId") Long companyId,
      @Param("taxCode") String taxCode,
      @Param("email") String email,
      @Param("phone") String phone);

  /**
   * Search suppliers by code or name using native PostgreSQL unaccent function.
   * Supports unaccented Vietnamese search (e.g., "nha" matches "nhà").
   *
   * @param companyId company ID
   * @param searchTerm search term (matches code or unaccented name)
   * @return list of matching suppliers
   */
  @Query(value = "SELECT * FROM suppliers s "
      + "WHERE s.company_id = :companyId "
      + "AND ("
      + "  s.code ILIKE '%' || :searchTerm || '%' "
      + "  OR unaccent_search(s.name) ILIKE '%' || unaccent_search(:searchTerm) || '%' "
      + ") "
      + "ORDER BY s.name", nativeQuery = true)
  List<Supplier> searchByCodeOrNameNative(
      @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);
}

