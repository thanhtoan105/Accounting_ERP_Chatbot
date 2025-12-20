package com.accounting.seed.seeder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.BankAccount;
import com.accounting.entity.BankAccount.AccountType;
import com.accounting.seed.UniqueGenerator;
import com.accounting.seed.VietnameseFaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BankAccountSeeder {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO bank_accounts (company_id, account_number, bank_name, branch, type, opening_balance, active, gl_account_code, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

    private static final String[] CASH_NAMES = {"Quỹ tiền mặt", "Quỹ tiền mặt USD"};

    @Transactional
    public List<BankAccount> seedBankAccounts(
            Long companyId, int count, VietnameseFaker faker, UniqueGenerator generator) {
        log.info("Seeding {} bank accounts for company {}", count, companyId);

        List<BankAccount> accounts = new ArrayList<>(count);
        int cashCount = Math.min(2, count);

        for (int i = 0; i < count; i++) {
            BankAccount account = new BankAccount();
            account.setCompanyId(companyId);
            account.setAccountNumber(generator.nextBankAccountNumber(companyId));
            account.setActive(true);

            if (i < cashCount) {
                account.setType(AccountType.CASH);
                account.setBankName(CASH_NAMES[i]);
                account.setBranch(null);
                account.setGlAccountCode("1111");
            } else {
                account.setType(AccountType.BANK);
                account.setBankName(faker.bankName());
                account.setBranch(faker.address());
                account.setGlAccountCode("1121");
            }

            long balance = (10 + faker.getFaker().random().nextLong(491)) * 1_000_000L;
            account.setOpeningBalance(BigDecimal.valueOf(balance));

            accounts.add(account);
        }

        List<Long> generatedIds = batchInsert(accounts);
        for (int i = 0; i < accounts.size(); i++) {
            accounts.get(i).setId(generatedIds.get(i));
        }

        log.info("Successfully seeded {} bank accounts for company {}", accounts.size(), companyId);
        return accounts;
    }

    private List<Long> batchInsert(List<BankAccount> accounts) {
        List<Long> ids = new ArrayList<>(accounts.size());

        for (BankAccount account : accounts) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(
                    connection -> {
                        PreparedStatement ps =
                                connection.prepareStatement(INSERT_SQL, new String[] {"id"});
                        ps.setLong(1, account.getCompanyId());
                        ps.setString(2, account.getAccountNumber());
                        ps.setString(3, account.getBankName());
                        if (account.getBranch() != null) {
                            ps.setString(4, account.getBranch());
                        } else {
                            ps.setNull(4, Types.VARCHAR);
                        }
                        ps.setString(5, account.getType().name());
                        ps.setBigDecimal(6, account.getOpeningBalance());
                        ps.setBoolean(7, account.getActive());
                        if (account.getGlAccountCode() != null) {
                            ps.setString(8, account.getGlAccountCode());
                        } else {
                            ps.setNull(8, Types.VARCHAR);
                        }
                        return ps;
                    },
                    keyHolder);
            ids.add(keyHolder.getKey().longValue());
        }

        return ids;
    }
}
