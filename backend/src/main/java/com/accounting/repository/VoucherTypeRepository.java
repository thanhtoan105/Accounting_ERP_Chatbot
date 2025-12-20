package com.accounting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.VoucherType;

public interface VoucherTypeRepository
    extends JpaRepository<VoucherType, Long>, JpaSpecificationExecutor<VoucherType> {
  
  List<VoucherType> findByCompanyId(Long companyId);

  Optional<VoucherType> findByCompanyIdAndTypeCode(Long companyId, String typeCode);

  @Query("SELECT vt FROM VoucherType vt WHERE vt.companyId = :companyId " +
         "AND (LOWER(vt.typeCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
         "OR LOWER(vt.typeName) LIKE LOWER(CONCAT('%', :search, '%')))")
  List<VoucherType> findByCompanyIdAndSearch(@Param("companyId") Long companyId, 
                                              @Param("search") String search);

  @Query("SELECT vt FROM VoucherType vt WHERE vt.companyId = :companyId AND vt.status = :status")
  List<VoucherType> findByCompanyIdAndStatus(@Param("companyId") Long companyId, 
                                             @Param("status") String status);

  @Query("SELECT vt FROM VoucherType vt WHERE vt.companyId = :companyId " +
         "AND vt.status = :status " +
         "AND (LOWER(vt.typeCode) LIKE LOWER(CONCAT('%', :search, '%')) " +
         "OR LOWER(vt.typeName) LIKE LOWER(CONCAT('%', :search, '%')))")
  List<VoucherType> findByCompanyIdAndStatusAndSearch(@Param("companyId") Long companyId,
                                                       @Param("status") String status,
                                                       @Param("search") String search);
}
