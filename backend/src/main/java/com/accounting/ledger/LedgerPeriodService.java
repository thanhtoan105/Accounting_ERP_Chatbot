package com.accounting.ledger;

public interface LedgerPeriodService {
  boolean isFirstPeriodClosed(long companyId);
}
