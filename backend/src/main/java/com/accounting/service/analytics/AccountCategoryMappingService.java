package com.accounting.service.analytics;

import java.util.List;

import com.accounting.entity.analytics.AccountCategory;
import com.accounting.entity.analytics.AccountCategoryMapping;

public interface AccountCategoryMappingService {

    AccountCategory categorizeAccount(String accountCode, Long companyId);

    List<AccountCategoryMapping> getMappingsForCompany(Long companyId);

    void evictCacheForCompany(Long companyId);
}
