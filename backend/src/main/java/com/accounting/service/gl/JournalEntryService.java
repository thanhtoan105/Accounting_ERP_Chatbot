package com.accounting.service.gl;

import java.util.List;

import com.accounting.entity.JournalEntry;
import com.accounting.entity.Voucher;

/**
 * Service for generating journal entries from vouchers.
 * Journal entries are immutable general ledger entries created when vouchers are posted.
 */
public interface JournalEntryService {

  /**
   * Generate journal entries for a posted voucher.
   * Creates one journal entry per voucher line (either debit or credit).
   *
   * @param voucher the posted voucher
   * @return list of created journal entries
   */
  List<JournalEntry> generateJournalEntries(Voucher voucher);
}
