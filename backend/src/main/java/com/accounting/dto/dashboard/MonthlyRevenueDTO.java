package com.accounting.dto.dashboard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing monthly revenue data for dashboard line/bar charts.
 * Contains time-series revenue data aggregated by month.
 */
public class MonthlyRevenueDTO {

    private List<MonthlyData> data;
    private BigDecimal totalRevenue;

    public MonthlyRevenueDTO() {
        this.data = new ArrayList<>();
    }

    public MonthlyRevenueDTO(List<MonthlyData> data, BigDecimal totalRevenue) {
        this.data = data != null ? data : new ArrayList<>();
        this.totalRevenue = totalRevenue;
    }

    public List<MonthlyData> getData() {
        return data;
    }

    public void setData(List<MonthlyData> data) {
        this.data = data;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    /**
     * Represents revenue data for a single month.
     */
    public static class MonthlyData {

        private String monthLabel;
        private BigDecimal revenue;

        public MonthlyData() {
        }

        public MonthlyData(String monthLabel, BigDecimal revenue) {
            this.monthLabel = monthLabel;
            this.revenue = revenue;
        }

        /**
         * Gets the month label in format "YYYY-MM" (e.g., "2024-01").
         *
         * @return the month label string
         */
        public String getMonthLabel() {
            return monthLabel;
        }

        public void setMonthLabel(String monthLabel) {
            this.monthLabel = monthLabel;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue(BigDecimal revenue) {
            this.revenue = revenue;
        }
    }
}
