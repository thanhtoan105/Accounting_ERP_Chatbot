package com.accounting.seed.seeder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingPeriodSeeder {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
        INSERT INTO accounting_periods (id, company_id, fiscal_year, period_number, period_name, 
            start_date, end_date, status, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    @Transactional
    public List<AccountingPeriod> seedPeriods(Long companyId, LocalDate startDate, LocalDate endDate) {
        log.info("Seeding accounting periods for company {} from {} to {}", companyId, startDate, endDate);

        List<AccountingPeriod> periods = new ArrayList<>();
        YearMonth current = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);

        while (!current.isAfter(end)) {
            AccountingPeriod period = createPeriod(companyId, current);
            periods.add(period);
            current = current.plusMonths(1);
        }

        if (periods.isEmpty()) {
            log.warn("No periods generated for company {} between {} and {}", companyId, startDate, endDate);
            return periods;
        }

        batchInsert(periods);
        log.info("Seeded {} accounting periods for company {}", periods.size(), companyId);

        return periods;
    }

    private AccountingPeriod createPeriod(Long companyId, YearMonth yearMonth) {
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue();

        AccountingPeriod period = new AccountingPeriod();
        period.setId(UUID.randomUUID());
        period.setCompanyId(companyId);
        period.setFiscalYear(year);
        period.setPeriodNumber(month);
        period.setPeriodName(String.format("Tháng %02d/%d", month, year));
        period.setStartDate(yearMonth.atDay(1));
        period.setEndDate(yearMonth.atEndOfMonth());
        period.setStatus(PeriodStatus.OPEN);
        period.setCreatedAt(Instant.now());
        period.setUpdatedAt(Instant.now());

        return period;
    }

    private void batchInsert(List<AccountingPeriod> periods) {
        List<Object[]> batchArgs = periods.stream()
            .map(p -> new Object[]{
                p.getId(),
                p.getCompanyId(),
                p.getFiscalYear(),
                p.getPeriodNumber(),
                p.getPeriodName(),
                java.sql.Date.valueOf(p.getStartDate()),
                java.sql.Date.valueOf(p.getEndDate()),
                p.getStatus().name(),
                Timestamp.from(p.getCreatedAt()),
                Timestamp.from(p.getUpdatedAt()),
                0L
            })
            .toList();

        jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);
    }
}
