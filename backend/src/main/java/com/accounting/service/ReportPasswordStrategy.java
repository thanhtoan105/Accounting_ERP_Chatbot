package com.accounting.service;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.Company;

public interface ReportPasswordStrategy {

  String generatePassword(Company company, AccountingPeriod period);
}
