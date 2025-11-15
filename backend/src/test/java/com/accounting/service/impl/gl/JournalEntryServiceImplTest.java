package com.accounting.service.impl.gl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.accounting.entity.JournalEntry;
import com.accounting.entity.Voucher;
import com.accounting.entity.VoucherLine;
import com.accounting.repository.VoucherLineRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalEntryServiceImplTest {

    @Mock
    private VoucherLineRepository voucherLineRepository;
    @Mock
    private com.accounting.repository.JournalEntryRepository journalEntryRepository;

    private JournalEntryServiceImpl journalEntryService;
    private UUID testVoucherId = UUID.randomUUID();
    private Long testCompanyId = 1L;

    @BeforeEach
    void setUp() {
        journalEntryService = new JournalEntryServiceImpl(journalEntryRepository, voucherLineRepository);
    }

    @Test
    void generateJournalEntries_createsOneEntryPerLine() {
        // Arrange
        Voucher voucher = createVoucher();
        VoucherLine debitLine = createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO);
        VoucherLine creditLine = createVoucherLine(2L, BigDecimal.ZERO, BigDecimal.valueOf(1000));
        List<VoucherLine> lines = List.of(debitLine, creditLine);

        when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                .thenReturn(lines);
        when(journalEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<JournalEntry> journalEntries = journalEntryService.generateJournalEntries(voucher);

        // Assert
        assertNotNull(journalEntries);
        assertEquals(2, journalEntries.size());

        // Verify debit entry
        JournalEntry debitEntry = journalEntries.get(0);
        assertEquals(testVoucherId, debitEntry.getVoucherId());
        assertEquals(1L, debitEntry.getAccountId());
        assertEquals(BigDecimal.valueOf(1000), debitEntry.getDebitAmount());
        assertEquals(BigDecimal.ZERO, debitEntry.getCreditAmount());
        assertEquals(testCompanyId, debitEntry.getCompanyId());
        assertNotNull(debitEntry.getPostedAt());

        // Verify credit entry
        JournalEntry creditEntry = journalEntries.get(1);
        assertEquals(testVoucherId, creditEntry.getVoucherId());
        assertEquals(2L, creditEntry.getAccountId());
        assertEquals(BigDecimal.ZERO, creditEntry.getDebitAmount());
        assertEquals(BigDecimal.valueOf(1000), creditEntry.getCreditAmount());
        assertEquals(testCompanyId, creditEntry.getCompanyId());
        assertNotNull(creditEntry.getPostedAt());
    }

    @Test
    void generateJournalEntries_copiesDimensionReferences() {
        // Arrange
        Voucher voucher = createVoucher();
        VoucherLine lineWithDimensions = createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO);
        lineWithDimensions.setCustomerId(10L);
        lineWithDimensions.setVendorId(20L);
        lineWithDimensions.setCostCenterId(30L);
        List<VoucherLine> lines = List.of(lineWithDimensions);

        when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                .thenReturn(lines);
        when(journalEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<JournalEntry> journalEntries = journalEntryService.generateJournalEntries(voucher);

        // Assert
        assertEquals(1, journalEntries.size());
        JournalEntry entry = journalEntries.get(0);
        assertEquals(10L, entry.getCustomerId());
        assertEquals(20L, entry.getSupplierId());
        assertEquals(30L, entry.getCostCenterId());
    }

    @Test
    void generateJournalEntries_setsPeriodIdFromVoucher() {
        // Arrange
        UUID testPeriodId = UUID.randomUUID();
        Voucher voucher = createVoucher();
        voucher.setPeriodId(testPeriodId);
        VoucherLine line = createVoucherLine(1L, BigDecimal.valueOf(1000), BigDecimal.ZERO);
        List<VoucherLine> lines = List.of(line);

        when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                .thenReturn(lines);
        when(journalEntryRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<JournalEntry> journalEntries = journalEntryService.generateJournalEntries(voucher);

        // Assert
        assertEquals(1, journalEntries.size());
        JournalEntry entry = journalEntries.get(0);
        assertEquals(testPeriodId, entry.getPeriodId());
    }

    @Test
    void generateJournalEntries_emptyLines_returnsEmptyList() {
        // Arrange
        Voucher voucher = createVoucher();
        when(voucherLineRepository.findByVoucherIdOrderByLineNumberAsc(testVoucherId))
                .thenReturn(List.of());

        // Act
        List<JournalEntry> journalEntries = journalEntryService.generateJournalEntries(voucher);

        // Assert
        assertNotNull(journalEntries);
        assertTrue(journalEntries.isEmpty());
    }

    // Helper methods
    private Voucher createVoucher() {
        Voucher voucher = new Voucher();
        voucher.setId(testVoucherId);
        voucher.setCompanyId(testCompanyId);
        voucher.setVoucherDate(LocalDate.now());
        voucher.setPeriodId(UUID.randomUUID());
        return voucher;
    }

    private VoucherLine createVoucherLine(Long accountId, BigDecimal debit, BigDecimal credit) {
        VoucherLine line = new VoucherLine();
        line.setVoucherId(testVoucherId);
        line.setAccountId(accountId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setCompanyId(testCompanyId);
        return line;
    }
}
