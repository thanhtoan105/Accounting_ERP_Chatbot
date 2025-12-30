package com.accounting.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.dashboard.AgingBucketsDTO;
import com.accounting.dto.dashboard.AgingBucketsDTO.AgingBucket;
import com.accounting.dto.dashboard.CashFlowDTO;
import com.accounting.dto.dashboard.CashFlowDTO.CashFlowData;
import com.accounting.dto.dashboard.ExpenseBreakdownDTO;
import com.accounting.dto.dashboard.ExpenseBreakdownDTO.ExpenseCategory;
import com.accounting.dto.dashboard.MonthlyRevenueDTO;
import com.accounting.dto.dashboard.MonthlyRevenueDTO.MonthlyData;
import com.accounting.repository.SalesInvoiceRepository;
import com.accounting.repository.VoucherLineRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.DashboardService;

/**
 * Implementation of DashboardService.
 * Provides dashboard chart data with 5-minute cache TTL.
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final VoucherLineRepository voucherLineRepository;

    public DashboardServiceImpl(SalesInvoiceRepository salesInvoiceRepository,
            VoucherLineRepository voucherLineRepository) {
        this.salesInvoiceRepository = salesInvoiceRepository;
        this.voucherLineRepository = voucherLineRepository;
    }

    @Override
    @Cacheable(value = "dashboard-aging", key = "'aging:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':' + #asOfDate")
    public AgingBucketsDTO getAgingBuckets(LocalDate asOfDate) {
        Long companyId = CompanyContext.getCompanyId();
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();

        logger.debug("Calculating aging buckets for company {} as of {}", companyId, date);

        List<AgingBucket> buckets = new ArrayList<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        // Calculate buckets based on due date vs asOfDate
        // CURRENT: dueDate >= asOfDate (not yet due)
        BigDecimal currentAmount = salesInvoiceRepository.getAgingBucketAmount(
                companyId, date, null);
        if (currentAmount == null) {
            currentAmount = BigDecimal.ZERO;
        }

        // DAYS_1_30: asOfDate - 30 <= dueDate < asOfDate
        BigDecimal days1_30 = salesInvoiceRepository.getAgingBucketAmountByRange(
                companyId, date.minusDays(30), date.minusDays(1));
        if (days1_30 == null) {
            days1_30 = BigDecimal.ZERO;
        }

        // DAYS_31_60: asOfDate - 60 <= dueDate < asOfDate - 30
        BigDecimal days31_60 = salesInvoiceRepository.getAgingBucketAmountByRange(
                companyId, date.minusDays(60), date.minusDays(31));
        if (days31_60 == null) {
            days31_60 = BigDecimal.ZERO;
        }

        // DAYS_61_90: asOfDate - 90 <= dueDate < asOfDate - 60
        BigDecimal days61_90 = salesInvoiceRepository.getAgingBucketAmountByRange(
                companyId, date.minusDays(90), date.minusDays(61));
        if (days61_90 == null) {
            days61_90 = BigDecimal.ZERO;
        }

        // DAYS_OVER_90: dueDate < asOfDate - 90
        BigDecimal over90 = salesInvoiceRepository.getAgingBucketAmountOver90(
                companyId, date.minusDays(90));
        if (over90 == null) {
            over90 = BigDecimal.ZERO;
        }

        totalOutstanding = currentAmount.add(days1_30).add(days31_60).add(days61_90).add(over90);

        // Calculate percentages
        buckets.add(createBucket("CURRENT", currentAmount, totalOutstanding));
        buckets.add(createBucket("DAYS_1_30", days1_30, totalOutstanding));
        buckets.add(createBucket("DAYS_31_60", days31_60, totalOutstanding));
        buckets.add(createBucket("DAYS_61_90", days61_90, totalOutstanding));
        buckets.add(createBucket("DAYS_OVER_90", over90, totalOutstanding));

        logger.debug("Aging buckets calculated: total={}, buckets={}", totalOutstanding, buckets.size());

        return new AgingBucketsDTO(buckets, totalOutstanding, date);
    }

    private AgingBucket createBucket(String key, BigDecimal amount, BigDecimal total) {
        BigDecimal percent = BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            percent = amount.multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
        }
        return new AgingBucket(key, amount, percent);
    }

    @Override
    @Cacheable(value = "dashboard-revenue", key = "'revenue:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':' + #months")
    public MonthlyRevenueDTO getMonthlyRevenue(int months) {
        Long companyId = CompanyContext.getCompanyId();
        int effectiveMonths = Math.max(1, Math.min(months, 24));

        logger.debug("Calculating monthly revenue for company {} for {} months", companyId, effectiveMonths);

        List<MonthlyData> data = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;

        YearMonth currentMonth = YearMonth.now();
        for (int i = effectiveMonths - 1; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDate startOfMonth = month.atDay(1);
            LocalDate endOfMonth = month.atEndOfMonth();

            BigDecimal monthlyRevenue = salesInvoiceRepository.getMonthlyRevenue(
                    companyId, startOfMonth, endOfMonth);
            if (monthlyRevenue == null) {
                monthlyRevenue = BigDecimal.ZERO;
            }

            data.add(new MonthlyData(month.format(MONTH_FORMATTER), monthlyRevenue));
            totalRevenue = totalRevenue.add(monthlyRevenue);
        }

        logger.debug("Monthly revenue calculated: total={}, months={}", totalRevenue, data.size());

        return new MonthlyRevenueDTO(data, totalRevenue);
    }

    @Override
    @Cacheable(value = "dashboard-cashflow", key = "'cashflow:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':' + #months")
    public CashFlowDTO getCashFlow(int months) {
        Long companyId = CompanyContext.getCompanyId();
        int effectiveMonths = Math.max(1, Math.min(months, 24));

        logger.debug("Calculating cash flow for company {} for {} months", companyId, effectiveMonths);

        List<CashFlowData> data = new ArrayList<>();
        BigDecimal totalInflow = BigDecimal.ZERO;
        BigDecimal totalOutflow = BigDecimal.ZERO;

        YearMonth currentMonth = YearMonth.now();
        for (int i = effectiveMonths - 1; i >= 0; i--) {
            YearMonth month = currentMonth.minusMonths(i);
            LocalDate startOfMonth = month.atDay(1);
            LocalDate endOfMonth = month.atEndOfMonth();

            // Query inflow (receipts) and outflow (payments)
            BigDecimal inflow = salesInvoiceRepository.getMonthlyInflow(
                    companyId, startOfMonth, endOfMonth);
            BigDecimal outflow = salesInvoiceRepository.getMonthlyOutflow(
                    companyId, startOfMonth, endOfMonth);

            if (inflow == null) {
                inflow = BigDecimal.ZERO;
            }
            if (outflow == null) {
                outflow = BigDecimal.ZERO;
            }

            CashFlowData monthData = new CashFlowData();
            monthData.setMonthLabel(month.format(MONTH_FORMATTER));
            monthData.setInflow(inflow);
            monthData.setOutflow(outflow);
            monthData.setNet(inflow.subtract(outflow));

            data.add(monthData);
            totalInflow = totalInflow.add(inflow);
            totalOutflow = totalOutflow.add(outflow);
        }

        CashFlowDTO result = new CashFlowDTO();
        result.setData(data);
        result.setTotalInflow(totalInflow);
        result.setTotalOutflow(totalOutflow);
        result.setNetCashFlow(totalInflow.subtract(totalOutflow));

        logger.debug("Cash flow calculated: inflow={}, outflow={}, net={}",
                totalInflow, totalOutflow, result.getNetCashFlow());

        return result;
    }

    @Override
    @Cacheable(value = "dashboard-expenses", key = "'expenses:' + T(com.accounting.security.CompanyContext).getCompanyId() + ':' + #startDate + ':' + #endDate")
    public ExpenseBreakdownDTO getExpenseBreakdown(LocalDate startDate, LocalDate endDate) {
        Long companyId = CompanyContext.getCompanyId();
        LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

        logger.debug("Calculating expense breakdown for company {} from {} to {}",
                companyId, effectiveStartDate, effectiveEndDate);

        ExpenseBreakdownDTO result = new ExpenseBreakdownDTO();
        result.setStartDate(effectiveStartDate);
        result.setEndDate(effectiveEndDate);

        // Query expense categories from VoucherLine (6xx accounts = expenses)
        List<Object[]> expenseData = voucherLineRepository.getExpensesByCategory(
                companyId, effectiveStartDate, effectiveEndDate);

        BigDecimal totalExpenses = BigDecimal.ZERO;
        List<ExpenseCategory> categories = new ArrayList<>();

        if (expenseData != null) {
            // First pass: calculate total
            for (Object[] row : expenseData) {
                BigDecimal amount = (BigDecimal) row[2];
                if (amount != null) {
                    totalExpenses = totalExpenses.add(amount);
                }
            }

            // Second pass: create categories with percentages
            for (Object[] row : expenseData) {
                Long categoryId = (Long) row[0];
                String categoryName = (String) row[1];
                BigDecimal amount = (BigDecimal) row[2];
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }

                ExpenseCategory category = new ExpenseCategory();
                category.setCategoryId(categoryId);
                category.setCategoryName(categoryName);
                category.setAmount(amount);

                BigDecimal percent = BigDecimal.ZERO;
                if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                    percent = amount.multiply(BigDecimal.valueOf(100))
                            .divide(totalExpenses, 2, RoundingMode.HALF_UP);
                }
                category.setPercent(percent);

                categories.add(category);
            }
        }

        result.setCategories(categories);
        result.setTotalExpenses(totalExpenses);

        logger.debug("Expense breakdown calculated: total={}, categories={}",
                totalExpenses, categories.size());

        return result;
    }
}
