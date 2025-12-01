package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO representing aging bucket amounts for a customer.
 * Contains amounts for each aging period: Current, 1-30d, 31-60d, 61-90d, 91+d.
 */
public class ARAgingBucketDTO {

    private BigDecimal current = BigDecimal.ZERO;
    private BigDecimal days1To30 = BigDecimal.ZERO;
    private BigDecimal days31To60 = BigDecimal.ZERO;
    private BigDecimal days61To90 = BigDecimal.ZERO;
    private BigDecimal daysOver90 = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;

    public BigDecimal getCurrent() {
        return current;
    }

    public void setCurrent(BigDecimal current) {
        this.current = current;
    }

    public BigDecimal getDays1To30() {
        return days1To30;
    }

    public void setDays1To30(BigDecimal days1To30) {
        this.days1To30 = days1To30;
    }

    public BigDecimal getDays31To60() {
        return days31To60;
    }

    public void setDays31To60(BigDecimal days31To60) {
        this.days31To60 = days31To60;
    }

    public BigDecimal getDays61To90() {
        return days61To90;
    }

    public void setDays61To90(BigDecimal days61To90) {
        this.days61To90 = days61To90;
    }

    public BigDecimal getDaysOver90() {
        return daysOver90;
    }

    public void setDaysOver90(BigDecimal daysOver90) {
        this.daysOver90 = daysOver90;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
