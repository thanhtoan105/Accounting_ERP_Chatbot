package com.accounting.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for F01 Detailed Ledger report.
 * Shows transaction-level detail with running balance per account.
 */
public class DetailedLedgerDTO {

  private Long companyId;
  private String companyName;
  private UUID periodId;
  private String periodName;
  private LocalDate startDate;
  private LocalDate endDate;

  // Account filter
  private String accountCode;
  private String accountName;

  // Subsidiary filter (optional)
  private String subsidiaryType;      // 'CUSTOMER', 'SUPPLIER', 'ITEM', null
  private Long subsidiaryId;
  private String subsidiaryName;

  // Opening balance
  private BigDecimal openingDebit;
  private BigDecimal openingCredit;
  private BigDecimal openingBalance;  // Net balance at period start

  // Transaction lines
  private List<DetailedLedgerLineDTO> lines;

  // Closing balance
  private BigDecimal totalDebit;
  private BigDecimal totalCredit;
  private BigDecimal closingBalance;  // Net balance at period end

  public DetailedLedgerDTO() {
    this.openingDebit = BigDecimal.ZERO;
    this.openingCredit = BigDecimal.ZERO;
    this.openingBalance = BigDecimal.ZERO;
    this.totalDebit = BigDecimal.ZERO;
    this.totalCredit = BigDecimal.ZERO;
    this.closingBalance = BigDecimal.ZERO;
    this.lines = new ArrayList<>();
  }

  // Getters and Setters

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public String getPeriodName() {
    return periodName;
  }

  public void setPeriodName(String periodName) {
    this.periodName = periodName;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public void setAccountCode(String accountCode) {
    this.accountCode = accountCode;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getSubsidiaryType() {
    return subsidiaryType;
  }

  public void setSubsidiaryType(String subsidiaryType) {
    this.subsidiaryType = subsidiaryType;
  }

  public Long getSubsidiaryId() {
    return subsidiaryId;
  }

  public void setSubsidiaryId(Long subsidiaryId) {
    this.subsidiaryId = subsidiaryId;
  }

  public String getSubsidiaryName() {
    return subsidiaryName;
  }

  public void setSubsidiaryName(String subsidiaryName) {
    this.subsidiaryName = subsidiaryName;
  }

  public BigDecimal getOpeningDebit() {
    return openingDebit;
  }

  public void setOpeningDebit(BigDecimal openingDebit) {
    this.openingDebit = openingDebit != null ? openingDebit : BigDecimal.ZERO;
  }

  public BigDecimal getOpeningCredit() {
    return openingCredit;
  }

  public void setOpeningCredit(BigDecimal openingCredit) {
    this.openingCredit = openingCredit != null ? openingCredit : BigDecimal.ZERO;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public void setOpeningBalance(BigDecimal openingBalance) {
    this.openingBalance = openingBalance != null ? openingBalance : BigDecimal.ZERO;
  }

  public List<DetailedLedgerLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<DetailedLedgerLineDTO> lines) {
    this.lines = lines != null ? lines : new ArrayList<>();
  }

  public BigDecimal getTotalDebit() {
    return totalDebit;
  }

  public void setTotalDebit(BigDecimal totalDebit) {
    this.totalDebit = totalDebit != null ? totalDebit : BigDecimal.ZERO;
  }

  public BigDecimal getTotalCredit() {
    return totalCredit;
  }

  public void setTotalCredit(BigDecimal totalCredit) {
    this.totalCredit = totalCredit != null ? totalCredit : BigDecimal.ZERO;
  }

  public BigDecimal getClosingBalance() {
    return closingBalance;
  }

  public void setClosingBalance(BigDecimal closingBalance) {
    this.closingBalance = closingBalance != null ? closingBalance : BigDecimal.ZERO;
  }

  /**
   * Add a line to this ledger.
   *
   * @param line the line to add
   */
  public void addLine(DetailedLedgerLineDTO line) {
    if (this.lines == null) {
      this.lines = new ArrayList<>();
    }
    this.lines.add(line);
  }

  /**
   * Calculate closing balance from opening and totals.
   * Closing = Opening + Total Debit - Total Credit
   */
  public void calculateClosingBalance() {
    this.closingBalance = openingBalance
        .add(totalDebit)
        .subtract(totalCredit);
  }

  /**
   * Nested DTO for a single ledger line (transaction).
   */
  public static class DetailedLedgerLineDTO {

    private UUID voucherId;
    private String voucherNumber;
    private LocalDate voucherDate;
    private String description;
    private String correspondingAccount;   // The other side of the entry
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal runningBalance;     // Cumulative balance after this line

    public DetailedLedgerLineDTO() {
      this.debit = BigDecimal.ZERO;
      this.credit = BigDecimal.ZERO;
      this.runningBalance = BigDecimal.ZERO;
    }

    // Getters and Setters

    public UUID getVoucherId() {
      return voucherId;
    }

    public void setVoucherId(UUID voucherId) {
      this.voucherId = voucherId;
    }

    public String getVoucherNumber() {
      return voucherNumber;
    }

    public void setVoucherNumber(String voucherNumber) {
      this.voucherNumber = voucherNumber;
    }

    public LocalDate getVoucherDate() {
      return voucherDate;
    }

    public void setVoucherDate(LocalDate voucherDate) {
      this.voucherDate = voucherDate;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getCorrespondingAccount() {
      return correspondingAccount;
    }

    public void setCorrespondingAccount(String correspondingAccount) {
      this.correspondingAccount = correspondingAccount;
    }

    public BigDecimal getDebit() {
      return debit;
    }

    public void setDebit(BigDecimal debit) {
      this.debit = debit != null ? debit : BigDecimal.ZERO;
    }

    public BigDecimal getCredit() {
      return credit;
    }

    public void setCredit(BigDecimal credit) {
      this.credit = credit != null ? credit : BigDecimal.ZERO;
    }

    public BigDecimal getRunningBalance() {
      return runningBalance;
    }

    public void setRunningBalance(BigDecimal runningBalance) {
      this.runningBalance = runningBalance != null ? runningBalance : BigDecimal.ZERO;
    }
  }
}
