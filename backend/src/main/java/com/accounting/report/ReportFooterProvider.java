package com.accounting.report;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides common footer parameters (logo & legal info) for reports.
 */
public class ReportFooterProvider {

    public Map<String, Object> buildFooterParams(String companyName, String taxCode, String address, String logoUrl) {
        Map<String, Object> params = new HashMap<>();
        params.put("COMPANY_NAME", companyName);
        params.put("COMPANY_TAX_CODE", taxCode);
        params.put("COMPANY_ADDRESS", address);
        params.put("COMPANY_LOGO_URL", logoUrl);
        return params;
    }
}
