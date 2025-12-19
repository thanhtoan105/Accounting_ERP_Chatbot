package com.accounting.service.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accounting.entity.analytics.AnalyticsAuditLog;
import com.accounting.entity.audit.AuditChainCheckpoint;
import com.accounting.repository.analytics.AnalyticsAuditLogRepository;
import com.accounting.repository.audit.AuditChainCheckpointRepository;
import com.accounting.service.audit.dto.AuditChainVerificationResult;
import com.accounting.service.audit.dto.AuditLogPayload;
import com.accounting.service.audit.impl.AuditHashChainServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditHashChainServiceImpl")
class AuditHashChainServiceImplTest {

    private static final String GENESIS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";
    private static final Long COMPANY_ID = 1L;
    private static final LocalDate TEST_DATE = LocalDate.of(2024, 1, 15);

    @Mock
    private AnalyticsAuditLogRepository auditLogRepository;

    @Mock
    private AuditChainCheckpointRepository checkpointRepository;

    @InjectMocks
    private AuditHashChainServiceImpl auditHashChainService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        auditHashChainService = new AuditHashChainServiceImpl(
                auditLogRepository,
                checkpointRepository,
                objectMapper);
    }

    private AuditLogPayload createTestPayload() {
        return AuditLogPayload.builder()
                .companyId(COMPANY_ID)
                .eventTime(Instant.parse("2024-01-15T10:00:00Z"))
                .eventType("VOUCHER")
                .eventSubtype("CREATE")
                .principalId(100L)
                .principalType("USER")
                .objectType("Voucher")
                .objectId("voucher-123")
                .ipAddress("192.168.1.1")
                .requestId(UUID.randomUUID())
                .requestPath("/api/vouchers")
                .requestMethod("POST")
                .responseStatus(200)
                .build();
    }

    private AnalyticsAuditLog createAuditLogWithValidHash(Long sequence, String prevHash) {
        Instant eventTime = Instant.parse("2024-01-15T10:00:00Z");
        AnalyticsAuditLog log = new AnalyticsAuditLog();
        log.setId(UUID.randomUUID());
        log.setCompanyId(COMPANY_ID);
        log.setEventTime(eventTime);
        log.setEventDateUtc(TEST_DATE);
        log.setEventType("VOUCHER");
        log.setEventSubtype("CREATE");
        log.setPrincipalId(100L);
        log.setPrincipalType("USER");
        log.setObjectType("Voucher");
        log.setObjectId("voucher-" + sequence);
        log.setSequenceInCompany(sequence);
        log.setSequenceInDay(sequence);
        log.setPrevHash(prevHash);

        String canonicalPayload = buildCanonicalPayload(log);
        String hashInput = prevHash + "|" + eventTime.toEpochMilli() + "|" + sequence + "|" + canonicalPayload;
        String recordHash = computeSha256Hex(hashInput);
        log.setRecordHash(recordHash);
        log.setMerkleLeafHash(computeSha256Hex(recordHash));

        return log;
    }

    private AnalyticsAuditLog createSimpleMockLog(Long sequence, String recordHash) {
        AnalyticsAuditLog log = new AnalyticsAuditLog();
        log.setId(UUID.randomUUID());
        log.setCompanyId(COMPANY_ID);
        log.setSequenceInCompany(sequence);
        log.setRecordHash(recordHash);
        return log;
    }

    private String buildCanonicalPayload(AnalyticsAuditLog log) {
        try {
            java.util.Map<String, Object> canonical = new java.util.TreeMap<>();
            canonical.put("companyId", log.getCompanyId());
            canonical.put("eventTime", log.getEventTime() != null ? log.getEventTime().toString() : null);
            canonical.put("eventType", log.getEventType());
            canonical.put("eventSubtype", log.getEventSubtype());
            canonical.put("principalId", log.getPrincipalId());
            canonical.put("principalType", log.getPrincipalType());
            canonical.put("objectType", log.getObjectType());
            canonical.put("objectId", log.getObjectId());
            canonical.put("ipAddress", log.getIpAddress());
            canonical.put("requestId", log.getRequestId() != null ? log.getRequestId().toString() : null);
            canonical.put("requestPath", log.getRequestPath());
            canonical.put("requestMethod", log.getRequestMethod());
            canonical.put("responseStatus", log.getResponseStatus());
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String computeSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("appendLog")
    class AppendLogTests {

        @Test
        @DisplayName("first record sets sequence to 1 and genesis prevHash")
        void appendLog_firstRecord_setsSequenceToOneAndNullPrevHash() {
            when(auditLogRepository.findTopByCompanyIdOrderBySequenceInCompanyDesc(COMPANY_ID))
                    .thenReturn(Optional.empty());
            when(auditLogRepository.countByCompanyIdAndEventDateUtc(eq(COMPANY_ID), any()))
                    .thenReturn(0L);
            when(auditLogRepository.save(any(AnalyticsAuditLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AuditLogPayload payload = createTestPayload();

            AnalyticsAuditLog result = auditHashChainService.appendLog(payload);

            assertThat(result.getSequenceInCompany()).isEqualTo(1L);
            assertThat(result.getSequenceInDay()).isEqualTo(1L);
            assertThat(result.getPrevHash()).isEqualTo(GENESIS_HASH);
            assertThat(result.getRecordHash()).isNotNull();
            assertThat(result.getRecordHash()).hasSize(64);
            assertThat(result.getRecordHash()).matches("[a-f0-9]{64}");
        }

        @Test
        @DisplayName("subsequent record sets correct sequence and prevHash")
        void appendLog_subsequentRecord_setsCorrectSequenceAndPrevHash() {
            String previousRecordHash = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1";

            AnalyticsAuditLog previousLog = createSimpleMockLog(5L, previousRecordHash);

            when(auditLogRepository.findTopByCompanyIdOrderBySequenceInCompanyDesc(COMPANY_ID))
                    .thenReturn(Optional.of(previousLog));
            when(auditLogRepository.countByCompanyIdAndEventDateUtc(eq(COMPANY_ID), any()))
                    .thenReturn(3L);
            when(auditLogRepository.save(any(AnalyticsAuditLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AuditLogPayload payload = createTestPayload();

            AnalyticsAuditLog result = auditHashChainService.appendLog(payload);

            assertThat(result.getSequenceInCompany()).isEqualTo(6L);
            assertThat(result.getSequenceInDay()).isEqualTo(4L);
            assertThat(result.getPrevHash()).isEqualTo(previousRecordHash);
        }
    }

    @Nested
    @DisplayName("computeMerkleRoot")
    class ComputeMerkleRootTests {

        @Test
        @DisplayName("single record returns that hash directly")
        void computeMerkleRoot_singleRecord_returnsRecordHash() {
            String singleHash = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1";

            String result = auditHashChainService.computeMerkleRoot(List.of(singleHash));

            assertThat(result).isEqualTo(singleHash);
        }

        @Test
        @DisplayName("even number of records computes correct root")
        void computeMerkleRoot_evenNumberOfRecords_computesCorrectRoot() {
            String hash1 = "1111111111111111111111111111111111111111111111111111111111111111";
            String hash2 = "2222222222222222222222222222222222222222222222222222222222222222";
            String hash3 = "3333333333333333333333333333333333333333333333333333333333333333";
            String hash4 = "4444444444444444444444444444444444444444444444444444444444444444";

            String combined12 = computeSha256Hex(hash1 + hash2);
            String combined34 = computeSha256Hex(hash3 + hash4);
            String expectedRoot = computeSha256Hex(combined12 + combined34);

            String result = auditHashChainService.computeMerkleRoot(Arrays.asList(hash1, hash2, hash3, hash4));

            assertThat(result).isEqualTo(expectedRoot);
        }

        @Test
        @DisplayName("odd number of records duplicates last and computes root")
        void computeMerkleRoot_oddNumberOfRecords_duplicatesLastAndComputesRoot() {
            String hash1 = "1111111111111111111111111111111111111111111111111111111111111111";
            String hash2 = "2222222222222222222222222222222222222222222222222222222222222222";
            String hash3 = "3333333333333333333333333333333333333333333333333333333333333333";

            String combined12 = computeSha256Hex(hash1 + hash2);
            String combined33 = computeSha256Hex(hash3 + hash3);
            String expectedRoot = computeSha256Hex(combined12 + combined33);

            String result = auditHashChainService.computeMerkleRoot(Arrays.asList(hash1, hash2, hash3));

            assertThat(result).isEqualTo(expectedRoot);
        }

        @Test
        @DisplayName("empty list returns hash of empty string")
        void computeMerkleRoot_emptyList_returnsHashOfEmptyString() {
            String expectedHash = computeSha256Hex("");

            String result = auditHashChainService.computeMerkleRoot(List.of());

            assertThat(result).isEqualTo(expectedHash);
        }
    }

    @Nested
    @DisplayName("verifyDailyChain")
    class VerifyDailyChainTests {

        @Test
        @DisplayName("consistent data returns verified true")
        void verifyDailyChain_consistentData_returnsVerifiedTrue() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);
            AnalyticsAuditLog record2 = createAuditLogWithValidHash(2L, record1.getRecordHash());

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Arrays.asList(record1, record2));

            String merkleRoot = auditHashChainService.computeMerkleRoot(
                    Arrays.asList(record1.getMerkleLeafHash(), record2.getMerkleLeafHash()));
            AuditChainCheckpoint checkpoint = new AuditChainCheckpoint();
            checkpoint.setCompanyId(COMPANY_ID);
            checkpoint.setEventDateUtc(TEST_DATE);
            checkpoint.setMerkleRoot(merkleRoot);

            when(checkpointRepository.findByCompanyIdAndEventDateUtc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Optional.of(checkpoint));

            AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(COMPANY_ID, TEST_DATE);

            assertThat(result.isVerified()).isTrue();
            assertThat(result.getStatus()).isEqualTo(AuditChainVerificationResult.STATUS_OK);
        }

        @Test
        @DisplayName("no records returns verified false with missing status")
        void verifyDailyChain_noRecords_returnsVerifiedFalseWithMissingStatus() {
            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of());

            AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(COMPANY_ID, TEST_DATE);

            assertThat(result.isVerified()).isFalse();
            assertThat(result.getStatus()).isEqualTo(AuditChainVerificationResult.STATUS_MISSING);
            assertThat(result.getMismatchReason()).contains("No records found");
        }

        @Test
        @DisplayName("tampered hash returns verified false with mismatch reason")
        void verifyDailyChain_tamperedHash_returnsVerifiedFalseWithMismatchReason() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);
            record1.setRecordHash("tampered_hash_not_matching_content_at_all_1234567890ab");

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of(record1));

            AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(COMPANY_ID, TEST_DATE);

            assertThat(result.isVerified()).isFalse();
            assertThat(result.getStatus()).isEqualTo(AuditChainVerificationResult.STATUS_MISMATCH);
            assertThat(result.getMismatchReason()).containsIgnoringCase("hash mismatch");
        }

        @Test
        @DisplayName("broken chain returns verified false with chain broken reason")
        void verifyDailyChain_brokenChain_returnsVerifiedFalseWithChainBroken() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);
            AnalyticsAuditLog record2 = createAuditLogWithValidHash(2L, "wrong_prev_hash_12345678901234567890123456789012");

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Arrays.asList(record1, record2));

            AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(COMPANY_ID, TEST_DATE);

            assertThat(result.isVerified()).isFalse();
            assertThat(result.getStatus()).isEqualTo(AuditChainVerificationResult.STATUS_MISMATCH);
            assertThat(result.getMismatchReason()).containsIgnoringCase("linkage");
        }

        @Test
        @DisplayName("merkle root mismatch with checkpoint returns verified false")
        void verifyDailyChain_merkleRootMismatch_returnsVerifiedFalse() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of(record1));

            AuditChainCheckpoint checkpoint = new AuditChainCheckpoint();
            checkpoint.setCompanyId(COMPANY_ID);
            checkpoint.setEventDateUtc(TEST_DATE);
            checkpoint.setMerkleRoot("different_merkle_root_12345678901234567890123456789012");

            when(checkpointRepository.findByCompanyIdAndEventDateUtc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Optional.of(checkpoint));

            AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(COMPANY_ID, TEST_DATE);

            assertThat(result.isVerified()).isFalse();
            assertThat(result.getMismatchReason()).containsIgnoringCase("merkle");
        }

        @Test
        @DisplayName("no existing checkpoint returns checkpointCreated true when chain valid")
        void verifyDailyChain_noCheckpoint_returnsCheckpointCreatedTrue() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of(record1));
            when(checkpointRepository.findByCompanyIdAndEventDateUtc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Optional.empty());

            AuditChainVerificationResult result = auditHashChainService.verifyDailyChain(COMPANY_ID, TEST_DATE);

            assertThat(result.isVerified()).isTrue();
            assertThat(result.isCheckpointCreated()).isTrue();
        }
    }

    @Nested
    @DisplayName("recomputeAndStoreDailyCheckpoint")
    class RecomputeAndStoreDailyCheckpointTests {

        @Test
        @DisplayName("stores checkpoint with correct merkle root")
        void recomputeAndStoreDailyCheckpoint_storesCorrectCheckpoint() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of(record1));
            when(checkpointRepository.findByCompanyIdAndEventDateUtc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Optional.empty());
            when(checkpointRepository.save(any(AuditChainCheckpoint.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditHashChainService.recomputeAndStoreDailyCheckpoint(COMPANY_ID, TEST_DATE);

            ArgumentCaptor<AuditChainCheckpoint> captor = ArgumentCaptor.forClass(AuditChainCheckpoint.class);
            verify(checkpointRepository).save(captor.capture());

            AuditChainCheckpoint saved = captor.getValue();
            assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
            assertThat(saved.getEventDateUtc()).isEqualTo(TEST_DATE);
            assertThat(saved.getMerkleRoot()).isEqualTo(record1.getMerkleLeafHash());
            assertThat(saved.getRecordCount()).isEqualTo(1L);
            assertThat(saved.getFirstSequence()).isEqualTo(1L);
            assertThat(saved.getLastSequence()).isEqualTo(1L);
        }

        @Test
        @DisplayName("no records does not save checkpoint")
        void recomputeAndStoreDailyCheckpoint_noRecords_doesNotSave() {
            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of());

            auditHashChainService.recomputeAndStoreDailyCheckpoint(COMPANY_ID, TEST_DATE);

            verify(checkpointRepository, never()).save(any());
        }

        @Test
        @DisplayName("updates existing checkpoint")
        void recomputeAndStoreDailyCheckpoint_updatesExistingCheckpoint() {
            AnalyticsAuditLog record1 = createAuditLogWithValidHash(1L, GENESIS_HASH);

            AuditChainCheckpoint existingCheckpoint = new AuditChainCheckpoint();
            existingCheckpoint.setId(99L);
            existingCheckpoint.setCompanyId(COMPANY_ID);
            existingCheckpoint.setEventDateUtc(TEST_DATE);
            existingCheckpoint.setMerkleRoot("old_merkle_root");

            when(auditLogRepository.findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(COMPANY_ID, TEST_DATE))
                    .thenReturn(List.of(record1));
            when(checkpointRepository.findByCompanyIdAndEventDateUtc(COMPANY_ID, TEST_DATE))
                    .thenReturn(Optional.of(existingCheckpoint));
            when(checkpointRepository.save(any(AuditChainCheckpoint.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditHashChainService.recomputeAndStoreDailyCheckpoint(COMPANY_ID, TEST_DATE);

            ArgumentCaptor<AuditChainCheckpoint> captor = ArgumentCaptor.forClass(AuditChainCheckpoint.class);
            verify(checkpointRepository).save(captor.capture());

            AuditChainCheckpoint saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(99L);
            assertThat(saved.getMerkleRoot()).isEqualTo(record1.getMerkleLeafHash());
        }
    }
}
