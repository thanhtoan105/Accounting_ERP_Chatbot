package com.accounting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.DefaultAccount;

public interface DefaultAccountRepository
    extends JpaRepository<DefaultAccount, Long>, JpaSpecificationExecutor<DefaultAccount> {

  List<DefaultAccount> findByCompanyId(Long companyId);

  List<DefaultAccount> findByCompanyIdAndVoucherType(Long companyId, String voucherType);

  @Query("SELECT da FROM DefaultAccount da WHERE da.companyId = :companyId "
      + "AND (LOWER(da.entryName) LIKE LOWER(CONCAT('%', :search, '%')) "
      + "OR LOWER(da.voucherType) LIKE LOWER(CONCAT('%', :search, '%')))")
  List<DefaultAccount> findByCompanyIdAndSearch(@Param("companyId") Long companyId,
      @Param("search") String search);

  @Query("SELECT da FROM DefaultAccount da WHERE da.companyId = :companyId AND da.status = :status")
  List<DefaultAccount> findByCompanyIdAndStatus(@Param("companyId") Long companyId,
      @Param("status") String status);

  @Query("SELECT da FROM DefaultAccount da WHERE da.companyId = :companyId "
      + "AND da.status = :status "
      + "AND (LOWER(da.entryName) LIKE LOWER(CONCAT('%', :search, '%')) "
      + "OR LOWER(da.voucherType) LIKE LOWER(CONCAT('%', :search, '%')))")
  List<DefaultAccount> findByCompanyIdAndStatusAndSearch(@Param("companyId") Long companyId,
      @Param("status") String status, @Param("search") String search);
}
