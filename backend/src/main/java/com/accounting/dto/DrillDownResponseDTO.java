package com.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for Trial Balance drill-down.
 * Contains paginated list of vouchers and summary information.
 */
public class DrillDownResponseDTO {

    private List<DrillDownVoucherDTO> vouchers;
    private BigDecimal totalAmount;
    private int voucherCount;

    // Pagination metadata
    private int page;
    private int size;
    private long total;
    private boolean hasNext;

    public DrillDownResponseDTO() {
        this.totalAmount = BigDecimal.ZERO;
    }

    // Getters and Setters

    public List<DrillDownVoucherDTO> getVouchers() {
        return vouchers;
    }

    public void setVouchers(List<DrillDownVoucherDTO> vouchers) {
        this.vouchers = vouchers;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    public int getVoucherCount() {
        return voucherCount;
    }

    public void setVoucherCount(int voucherCount) {
        this.voucherCount = voucherCount;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
}
