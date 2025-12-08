package com.accounting.service.impl.gl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.JournalEntry;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.JournalEntryRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.service.gl.JournalEntryService;

/**
 * Implementation of JournalEntryService.
 * Generates immutable journal entries from voucher lines when vouchers are posted.
 *
 * <p><strong>Transaction Behavior:</strong>
 * This service has a class-level {@code @Transactional} annotation, but it is typically
 * called from within another transaction (e.g., from {@code VoucherPostingService.postVoucher()}).
 * Spring will join the existing transaction rather than creating a new one, so journal entry
 * generation participates in the same atomic transaction as voucher posting. This ensures
 * that if journal entry generation fails, the entire posting operation rolls back.
 */
@Service
@Transactional
public class JournalEntryServiceImpl implements JournalEntryService {

  private final JournalEntryRepository journalEntryRepository;
  private final VoucherLineRepository voucherLineRepository;

  public JournalEntryServiceImpl(
      JournalEntryRepository journalEntryRepository,
      VoucherLineRepository voucherLineRepository) {
    this.journalEntryRepository = journalEntryRepository;
    this.voucherLineRepository = voucherLineRepository;
  }

  @Override
  public List<JournalEntry> generateJournalEntries(Voucher voucher) {
    List<JournalEntry> journalEntries = new ArrayList<>();

    // Get all voucher lines for this voucher
    List<VoucherLine> voucherLines = voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(voucher.getId());

    Instant now = Instant.now();

    for (VoucherLine line : voucherLines) {
      JournalEntry entry = new JournalEntry();
      entry.setVoucherId(voucher.getId());
      entry.setAccountId(line.getAccountId());
      entry.setPeriodId(voucher.getPeriodId());
      entry.setCompanyId(voucher.getCompanyId());
      entry.setPostedAt(now);
      entry.setCreatedAt(now);
      entry.setUpdatedAt(now);

      // Copy dimension references from voucher line
      entry.setCustomerId(line.getCustomerId());
      entry.setSupplierId(line.getVendorId()); // vendorId maps to supplierId in journal entry
      entry.setCostCenterId(line.getCostCenterId());

      // Set debit or credit amount (one must be > 0, the other must be 0)
      if (line.getDebit().compareTo(java.math.BigDecimal.ZERO) > 0) {
        entry.setDebitAmount(line.getDebit());
        entry.setCreditAmount(java.math.BigDecimal.ZERO);
      } else if (line.getCredit().compareTo(java.math.BigDecimal.ZERO) > 0) {
        entry.setDebitAmount(java.math.BigDecimal.ZERO);
        entry.setCreditAmount(line.getCredit());
      } else {
        // Skip lines with zero amounts (should not happen due to validation)
        continue;
      }

      journalEntries.add(entry);
    }

    // Save all journal entries
    return journalEntryRepository.saveAll(journalEntries);
  }
}
