package com.accounting.seed;

import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "app.seed")
public class SeedProperties {

    private boolean enabled = false;
    private long seed = 42L;
    private SeedProfile profile = SeedProfile.LARGE;

    private Integer companyCount;
    private Integer usersPerCompany;
    private Integer customersPerCompany;
    private Integer suppliersPerCompany;
    private Integer bankAccountsPerCompany;
    private Integer vouchersPerCompany;
    private Integer salesInvoicesPerCompany;
    private Integer purchaseBillsPerCompany;
    private Integer paymentsPerCompany;

    private LocalDate startDate = LocalDate.now().minusMonths(18);
    private LocalDate endDate = LocalDate.now();

    public enum SeedProfile {
        SMALL(2, 5, 50, 40, 3, 200, 150, 120, 50),
        MEDIUM(4, 12, 125, 100, 5, 1000, 750, 600, 250),
        LARGE(8, 25, 250, 200, 10, 2000, 1500, 1200, 500);

        private final int companies;
        private final int users;
        private final int customers;
        private final int suppliers;
        private final int bankAccounts;
        private final int vouchers;
        private final int salesInvoices;
        private final int purchaseBills;
        private final int payments;

        SeedProfile(
                int companies,
                int users,
                int customers,
                int suppliers,
                int bankAccounts,
                int vouchers,
                int salesInvoices,
                int purchaseBills,
                int payments) {
            this.companies = companies;
            this.users = users;
            this.customers = customers;
            this.suppliers = suppliers;
            this.bankAccounts = bankAccounts;
            this.vouchers = vouchers;
            this.salesInvoices = salesInvoices;
            this.purchaseBills = purchaseBills;
            this.payments = payments;
        }

        public int getCompanies() {
            return companies;
        }

        public int getUsers() {
            return users;
        }

        public int getCustomers() {
            return customers;
        }

        public int getSuppliers() {
            return suppliers;
        }

        public int getBankAccounts() {
            return bankAccounts;
        }

        public int getVouchers() {
            return vouchers;
        }

        public int getSalesInvoices() {
            return salesInvoices;
        }

        public int getPurchaseBills() {
            return purchaseBills;
        }

        public int getPayments() {
            return payments;
        }
    }

    public int getEffectiveCompanyCount() {
        return companyCount != null ? companyCount : profile.getCompanies();
    }

    public int getEffectiveUsersPerCompany() {
        return usersPerCompany != null ? usersPerCompany : profile.getUsers();
    }

    public int getEffectiveCustomersPerCompany() {
        return customersPerCompany != null ? customersPerCompany : profile.getCustomers();
    }

    public int getEffectiveSuppliersPerCompany() {
        return suppliersPerCompany != null ? suppliersPerCompany : profile.getSuppliers();
    }

    public int getEffectiveBankAccountsPerCompany() {
        return bankAccountsPerCompany != null ? bankAccountsPerCompany : profile.getBankAccounts();
    }

    public int getEffectiveVouchersPerCompany() {
        return vouchersPerCompany != null ? vouchersPerCompany : profile.getVouchers();
    }

    public int getEffectiveSalesInvoicesPerCompany() {
        return salesInvoicesPerCompany != null
                ? salesInvoicesPerCompany
                : profile.getSalesInvoices();
    }

    public int getEffectivePurchaseBillsPerCompany() {
        return purchaseBillsPerCompany != null
                ? purchaseBillsPerCompany
                : profile.getPurchaseBills();
    }

    public int getEffectivePaymentsPerCompany() {
        return paymentsPerCompany != null ? paymentsPerCompany : profile.getPayments();
    }
}
