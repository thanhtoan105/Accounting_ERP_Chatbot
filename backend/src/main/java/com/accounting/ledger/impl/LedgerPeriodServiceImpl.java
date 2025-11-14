package com.accounting.ledger.impl;

import com.accounting.ledger.LedgerPeriodService;
import org.springframework.stereotype.Service;

@Service
public class LedgerPeriodServiceImpl implements LedgerPeriodService {

  @Override
  public boolean isFirstPeriodClosed(long companyId) {
    // Placeholder: integrate with real ledger period repository when available.
    // For now, assume first period is open to allow imports.
    return false;
  }
}


