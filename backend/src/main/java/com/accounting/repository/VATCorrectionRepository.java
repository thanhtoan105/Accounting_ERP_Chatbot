package com.accounting.repository;

import com.accounting.entity.VATCorrection;
import com.accounting.entity.VATCorrection.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VATCorrectionRepository
    extends JpaRepository<VATCorrection, UUID>, JpaSpecificationExecutor<VATCorrection> {

  Optional<VATCorrection> findByCompanyIdAndId(Long companyId, UUID id);

  List<VATCorrection> findByCompanyIdAndPurchaseBillIdOrderByCorrectedAtDesc(
      Long companyId, UUID purchaseBillId);

  List<VATCorrection> findByCompanyIdAndPurchaseBillIdAndStatusOrderByCorrectedAtDesc(
      Long companyId, UUID purchaseBillId, Status status);

  List<VATCorrection> findByCompanyIdAndPurchaseBillIdAndCorrectedAtBetweenOrderByCorrectedAtDesc(
      Long companyId, UUID purchaseBillId, Instant start, Instant end);
}

