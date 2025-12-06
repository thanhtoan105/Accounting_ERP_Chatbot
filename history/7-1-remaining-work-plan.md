# Story 7.1 - Remaining Work Plan

**Created:** 2025-12-06
**Story Status:** ~70% Complete
**Remaining Effort:** ~3-4 hours

---

## Summary of Remaining Tasks

| Task | Priority | Effort | Dependency |
|------|----------|--------|------------|
| Task 4: PDF Export Service | HIGH | 2h | None |
| Task 5: PDF Controller Endpoint | HIGH | 30m | Task 4 |
| Task 10: VoucherDetailModal | MEDIUM | 1h | None |
| Task 11: PDF Export Button (Frontend) | HIGH | 30m | Task 5 |
| Task 12: Validation Preflight UI | MEDIUM | 30m | None |
| Task 13.2: Vietnamese i18n | LOW | 15m | None |
| Task 14: E2E Tests | MEDIUM | 1h | All above |

---

## Task 4: Backend - PDF Export Service (AC: #6) - HIGH PRIORITY

**Current State:** `TrialBalanceServiceImpl.exportToPdf()` at line 521 throws `UnsupportedOperationException`

**Implementation Plan:**

### 4.1 Create `TrialBalancePdfExportService` Interface
```java
// backend/src/main/java/com/accounting/service/report/TrialBalancePdfExportService.java
public interface TrialBalancePdfExportService {
    byte[] exportToPdf(UUID periodId, UUID snapshotId);
}
```

### 4.2 Implement `TrialBalancePdfExportServiceImpl`
**File:** `backend/src/main/java/com/accounting/service/impl/report/TrialBalancePdfExportServiceImpl.java`

**Pattern to follow:** Copy from `StatutoryReportExportService.java` (lines 316-463)

**Key steps:**
1. Inject `TrialBalanceService`, `PeriodManagementService`, `CompanyService`
2. Call `validateForExport()` first - throw `BusinessException` if invalid
3. Get trial balance data: `TrialBalanceResponseDTO data = trialBalanceService.getTrialBalanceData(periodId)`
4. Build DynamicReports PDF:
   - Define styles (title, subtitle, column headers, data rows)
   - Create columns: "Mã TK", "Tên tài khoản", "Dư Nợ đầu kỳ", "Dư Có đầu kỳ", "PS Nợ trong kỳ", "PS Có trong kỳ", "Dư Nợ cuối kỳ", "Dư Có cuối kỳ"
   - Create title component with company info and period
   - Add DRAFT watermark if period is open: `periodManagementService.isPeriodOpen(periodId)`
   - Add footer with timestamp and hash
5. Convert to byte array
6. Calculate SHA-256 hash for snapshot

**Vietnamese TT200 Layout:**
```
┌─────────────────────────────────────────────────────────────────┐
│  [Logo]  CÔNG TY ABC                                            │
│          MST: 0123456789                                        │
│          Địa chỉ: 123 ABC, Quận 1, TP.HCM                       │
│                                                                 │
│          BẢNG CÂN ĐỐI SỐ PHÁT SINH (S06-DN)                     │
│          Kỳ báo cáo: Tháng 11/2025 (01/11/2025 - 30/11/2025)    │
│                                                                 │
│          [DRAFT watermark if period open]                       │
├─────────────────────────────────────────────────────────────────┤
│ Mã TK │ Tên tài khoản │ Dư đầu kỳ    │ PS trong kỳ  │ Dư cuối kỳ │
│       │               │ Nợ    │ Có   │ Nợ    │ Có   │ Nợ   │ Có  │
├───────┼───────────────┼───────┼──────┼───────┼──────┼──────┼─────┤
│ 111   │ Tiền mặt      │ 1,000 │    0 │ 5,000 │3,000 │3,000 │   0 │
│ ...   │ ...           │   ... │  ... │   ... │  ... │  ... │ ... │
├───────┼───────────────┼───────┼──────┼───────┼──────┼──────┼─────┤
│       │ TỔNG CỘNG     │10,000 │10,000│50,000 │50,000│60,000│60,000│
└─────────────────────────────────────────────────────────────────┘
│ Ngày lập: 06/12/2025 14:30:00                                   │
│ Người lập: admin@example.com                                    │
│ SHA-256: abc123...                                              │
│ Snapshot ID: uuid-xxxx (if applicable)                          │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Wire into `TrialBalanceServiceImpl`
Replace the stub at line 521:
```java
@Override
public byte[] exportToPdf(UUID periodId, UUID snapshotId) {
    return trialBalancePdfExportService.exportToPdf(periodId, snapshotId);
}
```

### 4.4 Unit Tests
- Test PDF generation with valid data
- Test DRAFT watermark for open period
- Test validation failure throws exception
- Test hash calculation

---

## Task 5: Backend - PDF Controller Endpoint (AC: #6)

**Current State:** Endpoint exists but returns 500 due to stub

**Fix:** Wire the new service, update response headers
```java
@GetMapping("/export/pdf")
public ResponseEntity<byte[]> exportToPdf(
    @RequestParam UUID periodId,
    @RequestParam(required = false) UUID snapshotId) {
    
    byte[] pdfBytes = trialBalanceService.exportToPdf(periodId, snapshotId);
    String hash = calculateHash(pdfBytes);
    
    return ResponseEntity.ok()
        .header("Content-Type", "application/pdf")
        .header("Content-Disposition", "attachment; filename=trial-balance-" + periodId + ".pdf")
        .header("X-Content-SHA256", hash)
        .header("X-Snapshot-Id", snapshotId != null ? snapshotId.toString() : "")
        .body(pdfBytes);
}
```

---

## Task 10: Frontend - VoucherDetailModal (AC: #5)

**Pattern to follow:** Similar to existing voucher detail pages, DrillDownPanel.tsx

**File:** `frontend/src/features/accounting/pages/TrialBalance/VoucherDetailModal.tsx`

**Component Structure:**
```tsx
interface VoucherDetailModalProps {
  open: boolean;
  onClose: () => void;
  voucherId: string | null;
  breadcrumb?: { accountCode: string; amountType: string };
}

export function VoucherDetailModal({ open, onClose, voucherId, breadcrumb }: VoucherDetailModalProps) {
  const { t } = useTranslation();
  const { data: voucher, isLoading } = useQuery({
    queryKey: ['voucherDetail', voucherId],
    queryFn: () => getVoucherById(voucherId!),
    enabled: !!voucherId,
  });

  return (
    <Sheet open={open} onOpenChange={onClose}>
      <SheetContent className="w-[800px] sm:max-w-[800px]">
        {/* Breadcrumb */}
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-4">
          <span>{t('trialBalance.title')}</span>
          <ChevronRight className="h-4 w-4" />
          <span>{breadcrumb?.accountCode}</span>
          <ChevronRight className="h-4 w-4" />
          <span>{voucher?.voucherNumber}</span>
        </div>
        
        {/* Header */}
        <SheetHeader>
          <SheetTitle>{t('vouchers.detail.title')}</SheetTitle>
        </SheetHeader>
        
        {/* Voucher Summary */}
        <div className="grid grid-cols-2 gap-4 mt-4">
          <div><Label>Số chứng từ:</Label> {voucher?.voucherNumber}</div>
          <div><Label>Ngày:</Label> {formatDate(voucher?.voucherDate)}</div>
          <div><Label>Loại:</Label> {voucher?.voucherType}</div>
          <div><Label>Trạng thái:</Label> <Badge>{voucher?.status}</Badge></div>
        </div>
        
        {/* GL Lines Table */}
        <Table className="mt-4">
          <TableHeader>
            <TableRow>
              <TableHead>Tài khoản</TableHead>
              <TableHead>Diễn giải</TableHead>
              <TableHead className="text-right">Nợ</TableHead>
              <TableHead className="text-right">Có</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {voucher?.lines?.map((line) => (
              <TableRow key={line.id}>
                <TableCell>{line.accountCode}</TableCell>
                <TableCell>{line.description}</TableCell>
                <TableCell className="text-right">{formatCurrency(line.debit)}</TableCell>
                <TableCell className="text-right">{formatCurrency(line.credit)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        
        {/* Attachments */}
        {voucher?.attachments?.length > 0 && (
          <div className="mt-4">
            <Label>{t('vouchers.attachments')}</Label>
            <ul className="mt-2">
              {voucher.attachments.map((att) => (
                <li key={att.id}>
                  <a href={att.signedUrl} target="_blank" className="text-blue-600 underline">
                    {att.fileName}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
```

**Integration with DrillDownPanel:**
Add `onVoucherClick` handler to open modal instead of navigating to new page.

---

## Task 11: Frontend - PDF Export Button (AC: #6)

**File:** `frontend/src/features/accounting/pages/TrialBalance/TrialBalance.tsx`

**Add next to existing Excel button:**
```tsx
<Button
  variant="outline"
  onClick={handlePdfExport}
  disabled={isExportingPdf}
>
  {isExportingPdf ? (
    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
  ) : (
    <FileText className="mr-2 h-4 w-4" />
  )}
  {t('trialBalance.export.pdf')}
</Button>
```

**Handler:**
```tsx
const handlePdfExport = async () => {
  // 1. Validate first
  const validation = await validateTrialBalance(selectedPeriodId);
  if (!validation.valid) {
    setValidationError(validation.errors[0]);
    setShowValidationDialog(true);
    return;
  }
  
  // 2. Export
  setIsExportingPdf(true);
  try {
    const blob = await exportTrialBalancePdf(selectedPeriodId);
    downloadBlob(blob, `trial-balance-${selectedPeriodId}.pdf`);
    toast.success(t('trialBalance.export.pdfSuccess'));
  } catch (error) {
    toast.error(t('errors.exportFailed'));
  } finally {
    setIsExportingPdf(false);
  }
};
```

---

## Task 12: Frontend - Validation Preflight UI (AC: #9)

**Add Dialog component for validation errors:**
```tsx
<AlertDialog open={showValidationDialog} onOpenChange={setShowValidationDialog}>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle className="flex items-center gap-2 text-destructive">
        <AlertTriangle className="h-5 w-5" />
        {t('trialBalance.validation.failed')}
      </AlertDialogTitle>
      <AlertDialogDescription>
        {t('trialBalance.validation.glImbalance', {
          debit: formatCurrency(validationError?.details?.totalDebit),
          credit: formatCurrency(validationError?.details?.totalCredit),
        })}
      </AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <a href="/help/trial-balance-imbalance" target="_blank" className="text-blue-600">
        {t('trialBalance.validation.helpLink')}
      </a>
      <AlertDialogCancel>{t('common.cancel')}</AlertDialogCancel>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```

---

## Task 13.2: Vietnamese i18n Keys

**File:** `frontend/src/i18n/locales/vi/common.json`

**Add under `trialBalance` section:**
```json
"trialBalance": {
  "title": "Bảng Cân đối Phát sinh",
  "drillDown": {
    "title": "Chi tiết chứng từ",
    "openingDebit": "Dư Nợ đầu kỳ",
    "openingCredit": "Dư Có đầu kỳ",
    "periodDebit": "Phát sinh Nợ trong kỳ",
    "periodCredit": "Phát sinh Có trong kỳ",
    "closingDebit": "Dư Nợ cuối kỳ",
    "closingCredit": "Dư Có cuối kỳ",
    "voucherCount": "{{count}} chứng từ",
    "totalAmount": "Tổng số tiền",
    "date": "Ngày chứng từ",
    "voucherNumber": "Số chứng từ",
    "description": "Diễn giải",
    "debit": "Nợ",
    "credit": "Có",
    "noVouchers": "Không có chứng từ nào cho tiêu chí đã chọn"
  },
  "pagination": {
    "page": "Trang",
    "of": "trên",
    "previous": "Trước",
    "next": "Tiếp theo"
  },
  "export": {
    "pdf": "Xuất PDF",
    "pdfExporting": "Đang tạo PDF...",
    "pdfSuccess": "Xuất PDF thành công"
  },
  "validation": {
    "preflight": "Đang kiểm tra...",
    "failed": "Không thể xuất báo cáo",
    "glImbalance": "Bảng cân đối không cân bằng. Nợ: {{debit}}, Có: {{credit}}",
    "helpLink": "Xem hướng dẫn khắc phục"
  }
}
```

---

## Task 14: E2E Tests

**File:** `tests/e2e/trial-balance.spec.ts`

**Add tests for:**
1. **Drill-down flow:**
   - Click amount cell → panel opens
   - Pagination works
   - Sort works
   - Click voucher row → modal opens

2. **PDF export:**
   - Click PDF button → file downloads
   - Check response headers

3. **Validation preflight:**
   - Create imbalanced data → export blocked
   - Error dialog shows with correct message

---

## Execution Order

1. **Task 4** - PDF Export Service (backend) - 2h
2. **Task 5** - PDF Controller fix - 30m
3. **Task 11** - PDF button (frontend) - 30m
4. **Task 12** - Validation UI - 30m
5. **Task 10** - VoucherDetailModal - 1h
6. **Task 13.2** - Vietnamese i18n - 15m
7. **Task 14** - E2E tests - 1h

**Total estimated time:** 5-6 hours

---

## Verification Checklist

After implementation:
- [ ] `mvn test -Dtest="TrialBalanceServiceImplTest"` passes
- [ ] `mvn clean package` succeeds
- [ ] PDF export works for closed period (no watermark)
- [ ] PDF export shows DRAFT watermark for open period
- [ ] Validation blocks export when imbalanced
- [ ] VoucherDetailModal opens from drill-down
- [ ] Vietnamese language displays correctly
- [ ] E2E tests pass
