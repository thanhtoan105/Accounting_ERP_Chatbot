package com.accounting.service.bi;

import java.util.List;

import com.accounting.dto.bi.DataQualityIssue;
import com.accounting.dto.bi.DataQualitySummary;

public interface DataQualityService {

    List<DataQualityIssue> runAllChecks(Long companyId);

    DataQualitySummary getSummary(Long companyId);

    void runDailyChecks();
}
