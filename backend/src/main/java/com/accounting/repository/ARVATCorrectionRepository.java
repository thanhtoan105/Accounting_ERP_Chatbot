package com.accounting.repository;

import com.accounting.entity.ARVATCorrection;
import com.accounting.entity.ARVATCorrection.Status;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ARVATCorrectionRepository
    extends JpaRepository<ARVATCorrection, UUID>, JpaSpecificationExecutor<ARVATCorrection> {

  Optional<ARVATCorrection> findByCompanyIdAndId(Long companyId, UUID id);

  List<ARVATCorrection> findByCompanyIdAndInvoiceIdOrderByCorrectedAtDesc(
      Long companyId, UUID invoiceId);

  List<ARVATCorrection> findByCompanyIdAndInvoiceIdAndStatusOrderByCorrectedAtDesc(
      Long companyId, UUID invoiceId, Status status);

  List<ARVATCorrection> findByCompanyIdAndInvoiceIdAndCorrectedAtBetweenOrderByCorrectedAtDesc(
      Long companyId, UUID invoiceId, Instant start, Instant end);
}

