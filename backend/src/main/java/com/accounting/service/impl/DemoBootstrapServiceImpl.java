package com.accounting.service.impl;

import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.service.DemoBootstrapService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoBootstrapServiceImpl implements DemoBootstrapService {

    private static final String DEMO_CODE = "DEMO";

    private final CompanyRepository companyRepository;

    public DemoBootstrapServiceImpl(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional
    public Map<String, Object> bootstrapDemoCompany() {
        Map<String, Object> result = new HashMap<>();
        Company existing = companyRepository.findByCode(DEMO_CODE).orElse(null);
        boolean createdNow = false;
        if (existing == null) {
            Company c = new Company();
            c.setCode(DEMO_CODE);
            c.setName("Công ty Demo");
            c.setTaxCode("0123456789");
            c.setAddress("123 Demo Street, Hà Nội");
            c.setLogoUrl(null);
            existing = companyRepository.save(c);
            createdNow = true;
        }
        result.put("created", createdNow);
        result.put("companyId", existing.getId());
        result.put("code", existing.getCode());
        return result;
    }
}
