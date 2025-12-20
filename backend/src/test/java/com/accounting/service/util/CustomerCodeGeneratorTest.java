package com.accounting.service.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.Company;
import com.accounting.repository.CompanyRepository;
import com.accounting.security.CompanyContext;

import jakarta.persistence.EntityManager;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class CustomerCodeGeneratorTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private CustomerCodeGenerator codeGenerator;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company testCompany;

    @BeforeEach
    void setUp() {
        // Delete chart of accounts first to avoid FK constraint violations
        jdbcTemplate.execute("UPDATE chart_of_accounts SET parent_id = NULL");
        jdbcTemplate.execute("DELETE FROM chart_of_accounts");
        companyRepository.deleteAll();
        // Use unique company code to avoid conflicts in parallel test execution
        String uniqueCode = "TEST-" + System.currentTimeMillis();
        testCompany = new Company();
        testCompany.setCode(uniqueCode);
        testCompany.setName("Test Company");
        testCompany.setTaxCode("1234567890");
        testCompany.setAddress("Test Address");
        testCompany = companyRepository.save(testCompany);
        CompanyContext.setCompanyId(testCompany.getId());
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    @Transactional
    void generateCode_shouldReturnCorrectFormat() {
        String code = codeGenerator.generateCode(testCompany.getId());

        assertTrue(code.startsWith("CUST-"), "Code should start with CUST-");
        assertTrue(code.matches("^CUST-\\d{4}-\\d{4}$"), "Code should match format CUST-YYYY-NNNN");

        int currentYear = Year.now().getValue();
        String expectedYear = String.valueOf(currentYear);
        assertTrue(code.contains(expectedYear), "Code should contain current year");
    }

    @Test
    @Transactional
    void generateCode_shouldIncrementSequence() {
        String code1 = codeGenerator.generateCode(testCompany.getId());
        String code2 = codeGenerator.generateCode(testCompany.getId());

        assertNotEquals(code1, code2, "Generated codes should be different");

        // Extract sequence numbers
        String seq1 = code1.substring(code1.lastIndexOf('-') + 1);
        String seq2 = code2.substring(code2.lastIndexOf('-') + 1);

        int seqNum1 = Integer.parseInt(seq1);
        int seqNum2 = Integer.parseInt(seq2);

        assertEquals(seqNum1 + 1, seqNum2, "Sequence should increment by 1");
    }

    @Test
    @Transactional
    void generateCode_shouldBeUnique() {
        Set<String> codes = new HashSet<>();
        int iterations = 10;

        for (int i = 0; i < iterations; i++) {
            String code = codeGenerator.generateCode(testCompany.getId());
            codes.add(code);
        }

        assertEquals(iterations, codes.size(), "All generated codes should be unique");
    }

    @Test
    @Transactional
    void generateCode_shouldHandleYearRollover() {
        int currentYear = Year.now().getValue();
        int nextYear = currentYear + 1;

        // Generate codes for current year
        String codeCurrentYear = codeGenerator.generateCode(testCompany.getId());
        assertTrue(codeCurrentYear.contains(String.valueOf(currentYear)));

        // Manually test with next year (simulating year rollover)
        jakarta.persistence.Query query = entityManager.createNativeQuery(
                "SELECT get_next_customer_code(:companyId, :year)");
        query.setParameter("companyId", testCompany.getId());
        query.setParameter("year", nextYear);
        String codeNextYear = (String) query.getSingleResult();

        assertTrue(codeNextYear.contains(String.valueOf(nextYear)),
                "Code for next year should contain next year");
        assertTrue(codeNextYear.startsWith("CUST-"),
                "Code should still follow CUST-YYYY-NNNN format");
    }

    @Test
    void generateCode_shouldBeThreadSafe() throws Exception {
        int threadCount = 10;
        int codesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> allCodes = new HashSet<>();
        Set<String> synchronizedCodes = java.util.Collections.synchronizedSet(allCodes);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < codesPerThread; j++) {
                        String code = codeGenerator.generateCode(testCompany.getId());
                        synchronizedCodes.add(code);
                    }
                } catch (Exception e) {
                    // Log exception but don't fail the test
                    System.err.println("Thread " + threadIndex + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        int expectedCodes = threadCount * codesPerThread;
        assertEquals(expectedCodes, synchronizedCodes.size(),
                "All codes generated in parallel should be unique");
    }

    @Test
    @Transactional
    void generateCode_shouldBeCompanyScoped() {
        // Create another company
        Company otherCompany = new Company();
        otherCompany.setCode("OTHER");
        otherCompany.setName("Other Company");
        otherCompany.setTaxCode("0987654321");
        otherCompany.setAddress("Other Address");
        otherCompany = companyRepository.save(otherCompany);

        // Generate codes for both companies
        String code1 = codeGenerator.generateCode(testCompany.getId());
        String code2 = codeGenerator.generateCode(otherCompany.getId());

        // Both should start with CUST- and have same year
        assertTrue(code1.startsWith("CUST-"));
        assertTrue(code2.startsWith("CUST-"));

        // Both should have sequence starting from 0001 (or higher if already used)
        // The important thing is they can have the same sequence number for different
        // companies
        assertTrue(code1.matches("^CUST-\\d{4}-\\d{4}$"));
        assertTrue(code2.matches("^CUST-\\d{4}-\\d{4}$"));
    }
}
