package com.accounting.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for cash flow data used in stacked bar charts.
 * Provides monthly inflow/outflow breakdown for visualization.
 */
public class CashFlowDTO {

    private List<CashFlowData> data;
    private BigDecimal totalInflow;
    private BigDecimal totalOutflow;
    private BigDecimal netCashFlow;

    public List<CashFlowData> getData() {
        return data;
    }

    public void setData(List<CashFlowData> data) {
        this.data = data;
    }

    public BigDecimal getTotalInflow() {
        return totalInflow;
    }

    public void setTotalInflow(BigDecimal totalInflow) {
        this.totalInflow = totalInflow;
    }

    public BigDecimal getTotalOutflow() {
        return totalOutflow;
    }

    public void setTotalOutflow(BigDecimal totalOutflow) {
        this.totalOutflow = totalOutflow;
    }

    public BigDecimal getNetCashFlow() {
        return netCashFlow;
    }

    public void setNetCashFlow(BigDecimal netCashFlow) {
        this.netCashFlow = netCashFlow;
    }

    /**
     * Inner class representing cash flow data for a single month.
     */
    public static class CashFlowData {
        private String monthLabel;
        private BigDecimal inflow;
        private BigDecimal outflow;
        private BigDecimal net;

        public String getMonthLabel() {
            return monthLabel;
        }

        public void setMonthLabel(String monthLabel) {
            this.monthLabel = monthLabel;
        }

        public BigDecimal getInflow() {
            return inflow;
        }

        public void setInflow(BigDecimal inflow) {
            this.inflow = inflow;
        }

        public BigDecimal getOutflow() {
            return outflow;
        }

        public void setOutflow(BigDecimal outflow) {
            this.outflow = outflow;
        }

        public BigDecimal getNet() {
            return net;
        }

        public void setNet(BigDecimal net) {
            this.net = net;
        }
    }
}
