package com.accounting.service.impl.report;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.Company;
import com.accounting.service.ReportPasswordStrategy;

@Component
public class DefaultReportPasswordStrategy implements ReportPasswordStrategy {

  private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMyyyy");

  @Override
  public String generatePassword(Company company, AccountingPeriod period) {
    String taxCode = company.getTaxCode();
    String suffix =
        taxCode.length() >= 4 ? taxCode.substring(taxCode.length() - 4) : taxCode;
    return suffix + period.getEndDate().format(MONTH_YEAR_FORMAT);
  }
}
