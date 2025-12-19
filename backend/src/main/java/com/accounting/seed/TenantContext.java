package com.accounting.seed;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantContext {
    private Long companyId;
    private String companyCode;
    private List<Long> userIds;
    private List<Long> customerIds;
    private List<Long> supplierIds;
    private List<Long> bankAccountIds;
    private List<Long> cashBankAccountIds;
    private List<UUID> periodIds;
    private Map<String, Long> accountsByCode;
    private Map<String, List<Long>> accountsByPrefix;
}
