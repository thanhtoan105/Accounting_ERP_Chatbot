package com.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for AR dashboard metrics.
 * Provides overdue summary information for dashboard tiles.
 */
public class ARDashboardMetricsDTO {

    private BigDecimal totalOverdue;
    private Integer overdueCount;
    private List<TopOverdueCustomer> topOverdueCustomers;

    public BigDecimal getTotalOverdue() {
        return totalOverdue;
    }

    public void setTotalOverdue(BigDecimal totalOverdue) {
        this.totalOverdue = totalOverdue;
    }

    public Integer getOverdueCount() {
        return overdueCount;
    }

    public void setOverdueCount(Integer overdueCount) {
        this.overdueCount = overdueCount;
    }

    public List<TopOverdueCustomer> getTopOverdueCustomers() {
        return topOverdueCustomers;
    }

    public void setTopOverdueCustomers(List<TopOverdueCustomer> topOverdueCustomers) {
        this.topOverdueCustomers = topOverdueCustomers;
    }

    /**
     * Inner class for top overdue customer information.
     */
    public static class TopOverdueCustomer {
        private Long customerId;
        private String customerName;
        private BigDecimal overdueAmount;

        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }

        public BigDecimal getOverdueAmount() {
            return overdueAmount;
        }

        public void setOverdueAmount(BigDecimal overdueAmount) {
            this.overdueAmount = overdueAmount;
        }
    }
}
