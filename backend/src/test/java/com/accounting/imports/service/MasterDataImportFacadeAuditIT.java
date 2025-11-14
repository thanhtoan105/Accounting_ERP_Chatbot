package com.accounting.imports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.accounting.dto.ImportResultDTO;
import com.accounting.entity.AuditLog;
import com.accounting.entity.User;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportRowError;
import com.accounting.imports.model.ImportSummary;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class MasterDataImportFacadeAuditIT extends com.accounting.test.IntegrationTest {

    @Autowired
    private MasterDataImportFacade importFacade;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StubImportService stubImportService;
    @Autowired
    private com.accounting.repository.CompanyRepository companyRepository;

    private Long currentUserId;
    private Long currentCompanyId;

    @BeforeEach
    void setUp() {
        currentCompanyId = seedCompany();
        CompanyContext.setCompanyId(currentCompanyId);
        currentUserId = seedUser(currentCompanyId);
        SecurityContextHolder.getContext()
                .setAuthentication(authenticationFor(currentUserId, "ADMIN"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        CompanyContext.clear();
        List<AuditLog> logsForUser = auditLogRepository.findAll().stream()
                .filter(log -> currentUserId.equals(log.getUserId()))
                .toList();
        if (!logsForUser.isEmpty()) {
            auditLogRepository.deleteAll(logsForUser);
        }
        if (currentUserId != null) {
            userRepository.findById(currentUserId).ifPresent(userRepository::delete);
        }
        if (currentCompanyId != null) {
            companyRepository.findById(currentCompanyId).ifPresent(companyRepository::delete);
        }
    }

    @Test
    void process_successfulCustomerImport_shouldWriteAuditLogWithCounts() {
        stubImportService.willReturn(
                new ImportSummary(
                        5,
                        1,
                        2,
                        List.of(new ImportRowError(9, "tax_code", "Invalid format")),
                        UUID.randomUUID(),
                        List.of()));

        HttpServletRequest request = httpRequest("192.168.1.5", "JUnit/Success");
        ImportResultDTO result = importFacade.process(
                ImportType.CUSTOMERS,
                new MockMultipartFile("file", "customers.csv", "text/csv", new byte[0]),
                "en",
                request);

        assertThat(result.successCount()).isEqualTo(5);
        assertThat(result.errorCount()).isEqualTo(2);

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(entry -> currentUserId.equals(entry.getUserId()))
                .toList();
        assertThat(logs).hasSize(1);
        AuditLog log = logs.getFirst();
        assertThat(log.getAction()).isEqualTo("CUSTOMER_IMPORTED");
        assertThat(log.getReason()).contains("imported:5").contains("errors:2");
        assertThat(log.getUserId()).isEqualTo(currentUserId);
        assertThat(log.getEmail()).isEqualTo("admin@example.test");
        assertThat(log.getIpAddress()).isEqualTo("192.168.1.5");
        assertThat(log.getUserAgent()).isEqualTo("JUnit/Success");
    }

    @Test
    void process_failedCustomerImport_shouldWriteAuditLogWithErrorCountOnly() {
        stubImportService.willThrow(
                new ImportValidationException(
                        "Validation failed",
                        List.of(
                                new ImportRowError(3, "email", "Duplicate"),
                                new ImportRowError(5, "name", "Missing")),
                        UUID.randomUUID(),
                        List.of()));

        HttpServletRequest request = httpRequest("10.0.0.11", "JUnit/Failure");
        ImportResultDTO result = importFacade.process(
                ImportType.CUSTOMERS,
                new MockMultipartFile("file", "customers.csv", "text/csv", new byte[0]),
                "en",
                request);

        assertThat(result.successCount()).isZero();
        assertThat(result.errorCount()).isEqualTo(2);

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(entry -> currentUserId.equals(entry.getUserId()))
                .toList();
        assertThat(logs).hasSize(1);
        AuditLog log = logs.getFirst();
        assertThat(log.getAction()).isEqualTo("CUSTOMER_IMPORTED");
        assertThat(log.getReason()).contains("imported:0").contains("errors:2");
        assertThat(log.getUserId()).isEqualTo(currentUserId);
        assertThat(log.getIpAddress()).isEqualTo("10.0.0.11");
        assertThat(log.getUserAgent()).isEqualTo("JUnit/Failure");
    }

    private Long seedCompany() {
        com.accounting.entity.Company company = new com.accounting.entity.Company();
        company.setCode("AUDIT" + java.util.UUID.randomUUID().toString().substring(0, 6));
        company.setName("Test Company");
        company.setTaxCode(randomTaxCode());
        company.setAddress("123 Test Street");
        company.setContactEmail("contact@testco.example");
        company.setContactPhone("+84 90 000 0000");
        company.setFiscalYearStart(java.time.LocalDate.of(2025, 1, 1));
        return companyRepository.save(company).getId();
    }

    private String randomTaxCode() {
        long value = ThreadLocalRandom.current().nextLong(0, 1_000_000_0000L);
        return String.format("%010d", value);
    }

    private Long seedUser(Long companyId) {
        User user = new User();
        user.setEmail("admin@example.test");
        user.setPasswordHash("secret");
        user.setFullName("Admin User");
        user.setRole("admin");
        user.setCompanyId(companyId);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user).getId();
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(), "token", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private HttpServletRequest httpRequest(String remoteAddr, String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr != null ? remoteAddr : "127.0.0.1");
        request.addHeader("User-Agent", userAgent != null ? userAgent : "JUnit/Test");
        return request;
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        StubImportService stubImportService() {
            return new StubImportService();
        }
    }

    static class StubImportService implements MasterDataImportService {

        private ImportSummary nextSummary;
        private ImportValidationException nextException;

        @Override
        public ImportSummary importFile(
                ImportType type, org.springframework.web.multipart.MultipartFile file, ImportContext context) {
            if (nextException != null) {
                ImportValidationException exceptionToThrow = nextException;
                clear();
                throw exceptionToThrow;
            }
            ImportSummary summaryToReturn = nextSummary;
            clear();
            return summaryToReturn != null ? summaryToReturn : ImportSummary.success(0);
        }

        void willReturn(ImportSummary summary) {
            this.nextSummary = summary;
            this.nextException = null;
        }

        void willThrow(ImportValidationException exception) {
            this.nextException = exception;
            this.nextSummary = null;
        }

        private void clear() {
            this.nextSummary = null;
            this.nextException = null;
        }
    }
}
