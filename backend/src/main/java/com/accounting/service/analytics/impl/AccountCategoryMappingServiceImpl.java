package com.accounting.service.analytics.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.accounting.entity.analytics.AccountCategory;
import com.accounting.entity.analytics.AccountCategoryMapping;
import com.accounting.repository.analytics.AccountCategoryMappingRepository;
import com.accounting.service.analytics.AccountCategoryMappingService;
import com.accounting.service.analytics.AnalyticsCacheKeyGenerator;

@Service
public class AccountCategoryMappingServiceImpl implements AccountCategoryMappingService {

    private static final Logger log = LoggerFactory.getLogger(AccountCategoryMappingServiceImpl.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AccountCategoryMappingRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalyticsCacheKeyGenerator cacheKeyGenerator;

    public AccountCategoryMappingServiceImpl(
            AccountCategoryMappingRepository repository,
            RedisTemplate<String, Object> redisTemplate,
            AnalyticsCacheKeyGenerator cacheKeyGenerator) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.cacheKeyGenerator = cacheKeyGenerator;
    }

    @Override
    public AccountCategory categorizeAccount(String accountCode, Long companyId) {
        if (accountCode == null || accountCode.isBlank()) {
            log.debug("Empty account code provided, returning null category");
            return null;
        }

        List<AccountCategoryMapping> mappings = getMappingsForCompany(companyId);
        
        for (int prefixLen = accountCode.length(); prefixLen >= 1; prefixLen--) {
            String prefix = accountCode.substring(0, Math.min(prefixLen, 3));
            for (AccountCategoryMapping mapping : mappings) {
                if (accountCode.startsWith(mapping.getAccountCodePrefix())) {
                    log.debug("Account {} matched prefix {} -> category {}",
                            accountCode, mapping.getAccountCodePrefix(), mapping.getCategory());
                    return mapping.getCategory();
                }
            }
        }

        log.debug("No category mapping found for account {}", accountCode);
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AccountCategoryMapping> getMappingsForCompany(Long companyId) {
        String cacheKey = cacheKeyGenerator.coaMapping(companyId);

        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                log.debug("Cache hit for COA mappings, company: {}", companyId);
                return (List<AccountCategoryMapping>) cached;
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache: {}", e.getMessage());
        }

        log.debug("Cache miss for COA mappings, fetching from DB for company: {}", companyId);
        List<AccountCategoryMapping> dbMappings = repository.findByCompanyIdOrGlobal(companyId);
        
        Map<String, AccountCategoryMapping> effectiveMappings = dbMappings.stream()
                .collect(Collectors.toMap(
                        AccountCategoryMapping::getAccountCodePrefix,
                        m -> m,
                        (company, global) -> company.getCompanyId() != null ? company : global
                ));

        List<AccountCategoryMapping> result = new ArrayList<>(effectiveMappings.values());
        result.sort(Comparator.comparing(AccountCategoryMapping::getAccountCodePrefix).reversed());

        try {
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);
            log.debug("Cached {} COA mappings for company {}", result.size(), companyId);
        } catch (Exception e) {
            log.warn("Failed to cache COA mappings: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public void evictCacheForCompany(Long companyId) {
        String cacheKey = cacheKeyGenerator.coaMapping(companyId);
        try {
            redisTemplate.delete(cacheKey);
            log.info("Evicted COA mapping cache for company {}", companyId);
        } catch (Exception e) {
            log.warn("Failed to evict cache for company {}: {}", companyId, e.getMessage());
        }
    }
}
