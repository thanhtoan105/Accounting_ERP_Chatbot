package com.accounting.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for expense breakdown data used in pie charts.
 * Provides category-wise expense distribution for visualization.
 */
public class ExpenseBreakdownDTO {

    private List<ExpenseCategory> categories;
    private BigDecimal totalExpenses;
    private LocalDate startDate;
    private LocalDate endDate;

    public List<ExpenseCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<ExpenseCategory> categories) {
        this.categories = categories;
    }

    public BigDecimal getTotalExpenses() {
        return totalExpenses;
    }

    public void setTotalExpenses(BigDecimal totalExpenses) {
        this.totalExpenses = totalExpenses;
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

    /**
     * Inner class representing a single expense category with its amount and percentage.
     */
    public static class ExpenseCategory {
        private Long categoryId;
        private String categoryName;
        private BigDecimal amount;
        private BigDecimal percent;

        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(Long categoryId) {
            this.categoryId = categoryId;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public BigDecimal getPercent() {
            return percent;
        }

        public void setPercent(BigDecimal percent) {
            this.percent = percent;
        }
    }
}
