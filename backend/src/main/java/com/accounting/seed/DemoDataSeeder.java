package com.accounting.seed;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.BankAccount;
import com.accounting.entity.Company;
import com.accounting.entity.Customer;
import com.accounting.entity.Supplier;
import com.accounting.entity.User;
import com.accounting.seed.seeder.AccountingPeriodSeeder;
import com.accounting.seed.seeder.ApprovalWorkflowSeeder;
import com.accounting.seed.seeder.BankAccountSeeder;
import com.accounting.seed.seeder.CompanySeeder;
import com.accounting.seed.seeder.CustomerSeeder;
import com.accounting.seed.seeder.PaymentSeeder;
import com.accounting.seed.seeder.PurchaseBillSeeder;
import com.accounting.seed.seeder.SalesInvoiceSeeder;
import com.accounting.seed.seeder.SupplierSeeder;
import com.accounting.seed.seeder.UserSeeder;
import com.accounting.seed.seeder.VoucherSeeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private final SeedProperties seedProperties;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    private final CompanySeeder companySeeder;
    private final UserSeeder userSeeder;
    private final AccountingPeriodSeeder accountingPeriodSeeder;
    private final CustomerSeeder customerSeeder;
    private final SupplierSeeder supplierSeeder;
    private final BankAccountSeeder bankAccountSeeder;
    private final VoucherSeeder voucherSeeder;
    private final PurchaseBillSeeder purchaseBillSeeder;
    private final SalesInvoiceSeeder salesInvoiceSeeder;
    private final ApprovalWorkflowSeeder approvalWorkflowSeeder;
    private final PaymentSeeder paymentSeeder;

    private final Map<String, Integer> totalCounts = new HashMap<>();

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.isEnabled()) {
            log.info("Demo data seeding is disabled (app.seed.enabled=false)");
            return;
        }

        Instant startTime = Instant.now();
        log.info(
                "Starting demo data seeding with profile: {}, seed: {}",
                seedProperties.getProfile(),
                seedProperties.getSeed());
        logSeedingConfiguration();

        Random random = new Random(seedProperties.getSeed());
        VietnameseFaker faker = new VietnameseFaker(seedProperties.getSeed());
        UniqueGenerator generator = new UniqueGenerator(seedProperties.getSeed());

        int companyCount = seedProperties.getEffectiveCompanyCount();
        List<Company> companies = companySeeder.seedCompanies(companyCount, faker, generator);
        incrementCount("companies", companies.size());

        int successfulCompanies = 0;
        int failedCompanies = 0;

        for (int i = 0; i < companies.size(); i++) {
            Company company = companies.get(i);
            log.info(
                    "Seeding company {}/{}: {} (ID: {})",
                    i + 1,
                    companies.size(),
                    company.getName(),
                    company.getId());

            try {
                seedCompanyData(company, faker, generator, random);
                successfulCompanies++;
            } catch (Exception e) {
                failedCompanies++;
                log.error(
                        "Failed to seed data for company {} (ID: {}): {}",
                        company.getName(),
                        company.getId(),
                        e.getMessage(),
                        e);
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());
        logCompletionSummary(duration, successfulCompanies, failedCompanies);
    }

    private void seedCompanyData(
            Company company, VietnameseFaker faker, UniqueGenerator generator, Random random) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.execute(status -> {
            Long companyId = company.getId();
            String companyCode = company.getCode();
            int year = seedProperties.getEndDate().getYear();

            seedChartOfAccounts(companyId);
            Map<String, Long> accountsByCode = loadChartOfAccounts(companyId);
            Map<String, List<Long>> accountsByPrefix = buildAccountsByPrefix(accountsByCode);

            List<User> users =
                    userSeeder.seedUsers(
                            companyId,
                            companyCode,
                            seedProperties.getEffectiveUsersPerCompany(),
                            faker);
            List<Long> userIds = users.stream().map(User::getId).toList();
            incrementCount("users", users.size());
            log.info("  - Generated {} users", users.size());

            List<AccountingPeriod> periods =
                    accountingPeriodSeeder.seedPeriods(
                            companyId, seedProperties.getStartDate(), seedProperties.getEndDate());
            List<UUID> periodIds = periods.stream().map(AccountingPeriod::getId).toList();
            incrementCount("periods", periods.size());
            log.info("  - Generated {} periods", periods.size());

            List<Customer> customers =
                    customerSeeder.seedCustomers(
                            companyId,
                            seedProperties.getEffectiveCustomersPerCompany(),
                            faker,
                            generator,
                            year);
            List<Long> customerIds = customers.stream().map(Customer::getId).toList();
            incrementCount("customers", customers.size());
            log.info("  - Generated {} customers", customers.size());

            List<Supplier> suppliers =
                    supplierSeeder.seedSuppliers(
                            companyId,
                            seedProperties.getEffectiveSuppliersPerCompany(),
                            faker,
                            generator,
                            year);
            List<Long> supplierIds = suppliers.stream().map(Supplier::getId).toList();
            incrementCount("suppliers", suppliers.size());
            log.info("  - Generated {} suppliers", suppliers.size());

            List<BankAccount> bankAccounts =
                    bankAccountSeeder.seedBankAccounts(
                            companyId,
                            seedProperties.getEffectiveBankAccountsPerCompany(),
                            faker,
                            generator);
            List<Long> bankAccountIds = bankAccounts.stream()
                    .filter(ba -> ba.getType() == BankAccount.AccountType.BANK)
                    .map(BankAccount::getId)
                    .toList();
            List<Long> cashBankAccountIds = bankAccounts.stream()
                    .filter(ba -> ba.getType() == BankAccount.AccountType.CASH)
                    .map(BankAccount::getId)
                    .toList();
            incrementCount("bankAccounts", bankAccounts.size());
            log.info("  - Generated {} bank accounts ({} cash, {} bank)", 
                    bankAccounts.size(), cashBankAccountIds.size(), bankAccountIds.size());

            TenantContext ctx = TenantContext.builder()
                    .companyId(companyId)
                    .companyCode(companyCode)
                    .userIds(userIds)
                    .customerIds(customerIds)
                    .supplierIds(supplierIds)
                    .bankAccountIds(bankAccountIds)
                    .cashBankAccountIds(cashBankAccountIds)
                    .periodIds(periodIds)
                    .accountsByCode(accountsByCode)
                    .accountsByPrefix(accountsByPrefix)
                    .build();

            VoucherSeeder.VoucherResult voucherResult =
                    voucherSeeder.seedVouchers(
                            ctx,
                            seedProperties.getEffectiveVouchersPerCompany(),
                            faker,
                            generator,
                            periodIds,
                            random);
            incrementCount("vouchers", voucherResult.voucherIds().size());
            log.info("  - Generated {} vouchers", voucherResult.voucherIds().size());

            PurchaseBillSeeder.PurchaseBillResult purchaseBillResult =
                    purchaseBillSeeder.seedPurchaseBills(
                            ctx,
                            seedProperties.getEffectivePurchaseBillsPerCompany(),
                            faker,
                            generator,
                            random);
            incrementCount("purchaseBills", purchaseBillResult.billIds().size());
            log.info("  - Generated {} purchase bills", purchaseBillResult.billIds().size());

            SalesInvoiceSeeder.SalesInvoiceResult salesInvoiceResult =
                    salesInvoiceSeeder.seedSalesInvoices(
                            ctx,
                            seedProperties.getEffectiveSalesInvoicesPerCompany(),
                            faker,
                            generator,
                            random);
            incrementCount("salesInvoices", salesInvoiceResult.invoiceIds().size());
            log.info("  - Generated {} sales invoices", salesInvoiceResult.invoiceIds().size());

            var workflows = approvalWorkflowSeeder.seedApprovalWorkflows(
                    ctx, purchaseBillResult.billInfo(), salesInvoiceResult.invoiceInfo(), random);
            incrementCount("approvalWorkflows", workflows.size());
            log.info("  - Generated {} approval workflows", workflows.size());

            int apPaymentCount = seedProperties.getEffectivePaymentsPerCompany() / 2;
            var apPayments = paymentSeeder.seedAPPayments(
                    ctx, purchaseBillResult.billInfo(), apPaymentCount, faker, generator, random);
            incrementCount("apPayments", apPayments.size());
            log.info("  - Generated {} AP payments", apPayments.size());

            int arPaymentCount = seedProperties.getEffectivePaymentsPerCompany() / 2;
            var arPayments = paymentSeeder.seedARPayments(
                    ctx, salesInvoiceResult.invoiceInfo(), arPaymentCount, faker, generator, random);
            incrementCount("arPayments", arPayments.size());
            log.info("  - Generated {} AR payments", arPayments.size());

            return null;
        });
    }

    private void seedChartOfAccounts(Long companyId) {
        jdbcTemplate.query(
                "SELECT seed_tt200_coa_from_template(?)",
                ps -> ps.setLong(1, companyId),
                rs -> null);
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chart_of_accounts WHERE company_id = ?",
                Integer.class,
                companyId);
        log.info("  - Seeded TT200 chart of accounts ({} accounts)", count);
    }

    private Map<String, Long> loadChartOfAccounts(Long companyId) {
        String sql = "SELECT id, code FROM chart_of_accounts WHERE company_id = ? AND postable = true";
        return jdbcTemplate.query(
                sql,
                ps -> ps.setLong(1, companyId),
                rs -> {
                    Map<String, Long> map = new HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getString("code"), rs.getLong("id"));
                    }
                    return map;
                });
    }

    private Map<String, List<Long>> buildAccountsByPrefix(Map<String, Long> accountsByCode) {
        Map<String, List<Long>> byPrefix = new HashMap<>();
        for (Map.Entry<String, Long> entry : accountsByCode.entrySet()) {
            String code = entry.getKey();
            Long id = entry.getValue();
            for (int len = 1; len <= Math.min(code.length(), 3); len++) {
                String prefix = code.substring(0, len);
                byPrefix.computeIfAbsent(prefix, k -> new ArrayList<>()).add(id);
            }
        }
        return byPrefix;
    }

    private void logSeedingConfiguration() {
        log.info("Seeding configuration:");
        log.info("  - Companies: {}", seedProperties.getEffectiveCompanyCount());
        log.info("  - Users per company: {}", seedProperties.getEffectiveUsersPerCompany());
        log.info("  - Customers per company: {}", seedProperties.getEffectiveCustomersPerCompany());
        log.info("  - Suppliers per company: {}", seedProperties.getEffectiveSuppliersPerCompany());
        log.info("  - Bank accounts per company: {}", seedProperties.getEffectiveBankAccountsPerCompany());
        log.info("  - Vouchers per company: {}", seedProperties.getEffectiveVouchersPerCompany());
        log.info("  - Sales invoices per company: {}", seedProperties.getEffectiveSalesInvoicesPerCompany());
        log.info("  - Purchase bills per company: {}", seedProperties.getEffectivePurchaseBillsPerCompany());
        log.info("  - Payments per company: {}", seedProperties.getEffectivePaymentsPerCompany());
        log.info("  - Date range: {} to {}", seedProperties.getStartDate(), seedProperties.getEndDate());
    }

    private void logCompletionSummary(Duration duration, int successfulCompanies, int failedCompanies) {
        log.info("=".repeat(60));
        log.info("Demo data seeding completed in {}", formatDuration(duration));
        log.info("Companies: {} successful, {} failed", successfulCompanies, failedCompanies);
        log.info("Total records seeded:");
        totalCounts.forEach((entity, count) -> log.info("  - {}: {}", entity, count));
        log.info("=".repeat(60));
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else if (seconds > 0) {
            return String.format("%d.%03ds", seconds, millis);
        } else {
            return String.format("%dms", millis);
        }
    }

    private void incrementCount(String entity, int count) {
        totalCounts.merge(entity, count, Integer::sum);
    }
}
