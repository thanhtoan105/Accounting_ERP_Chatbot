package com.accounting.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.accounting.dto.VoucherTemplateDTO;
import com.accounting.dto.VoucherTemplateRequest;
import com.accounting.dto.VoucherTemplateSummaryDTO;

public interface VoucherTemplateService {

  List<VoucherTemplateSummaryDTO> list(Boolean isActive);

  Optional<VoucherTemplateDTO> getById(UUID id);

  VoucherTemplateDTO create(VoucherTemplateRequest request);

  VoucherTemplateDTO update(UUID id, VoucherTemplateRequest request);

  void delete(UUID id);

  VoucherTemplateDTO activate(UUID id);

  VoucherTemplateDTO deactivate(UUID id);
}
