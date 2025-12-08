package com.accounting.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.VATCorrection;
import com.accounting.entity.VATCorrection.Status;

public interface VATCorrectionRepository
        extends JpaRepository<VATCorrection, UUID>, JpaSpecificationExecutor<VATCorrection> {

    Optional<VATCorrection> findByCompanyIdAndId(Long companyId, UUID id);

    @Query("SELECT v FROM VATCorrection v WHERE v.companyId = :companyId "
            + "AND v.documentId = :purchaseBillId ORDER BY v.correctedAt DESC")
    List<VATCorrection> findByCompanyIdAndPurchaseBillIdOrderByCorrectedAtDesc(
            @Param("companyId") Long companyId, @Param("purchaseBillId") UUID purchaseBillId);

    @Query("SELECT v FROM VATCorrection v WHERE v.companyId = :companyId "
            + "AND v.documentId = :purchaseBillId AND v.status = :status ORDER BY v.correctedAt DESC")
    List<VATCorrection> findByCompanyIdAndPurchaseBillIdAndStatusOrderByCorrectedAtDesc(
            @Param("companyId") Long companyId,
            @Param("purchaseBillId") UUID purchaseBillId,
            @Param("status") Status status);

    @Query("SELECT v FROM VATCorrection v WHERE v.companyId = :companyId "
            + "AND v.documentId = :purchaseBillId AND v.correctedAt BETWEEN :start AND :end "
            + "ORDER BY v.correctedAt DESC")
    List<VATCorrection> findByCompanyIdAndPurchaseBillIdAndCorrectedAtBetweenOrderByCorrectedAtDesc(
            @Param("companyId") Long companyId,
            @Param("purchaseBillId") UUID purchaseBillId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
