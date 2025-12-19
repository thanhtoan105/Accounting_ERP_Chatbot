package com.accounting.service.audit.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.analytics.AnalyticsAuditLog;
import com.accounting.entity.audit.AuditChainCheckpoint;
import com.accounting.repository.analytics.AnalyticsAuditLogRepository;
import com.accounting.repository.audit.AuditChainCheckpointRepository;
import com.accounting.service.audit.AuditHashChainService;
import com.accounting.service.audit.dto.AuditChainVerificationResult;
import com.accounting.service.audit.dto.AuditLogPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuditHashChainServiceImpl implements AuditHashChainService {

    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final AnalyticsAuditLogRepository auditLogRepository;
    private final AuditChainCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;

    @Override
    public AnalyticsAuditLog appendLog(AuditLogPayload payload) {
        Optional<AnalyticsAuditLog> lastRecord = auditLogRepository
                .findTopByCompanyIdOrderBySequenceInCompanyDesc(payload.getCompanyId());

        long nextSequence = lastRecord.map(r -> r.getSequenceInCompany() + 1).orElse(1L);
        String prevHash = lastRecord.map(AnalyticsAuditLog::getRecordHash).orElse(GENESIS_HASH);

        LocalDate eventDateUtc = payload.getEventTime()
                .atZone(ZoneOffset.UTC)
                .toLocalDate();

        long countForDay = auditLogRepository.countByCompanyIdAndEventDateUtc(
                payload.getCompanyId(), eventDateUtc);
        long sequenceInDay = countForDay + 1;

        String canonicalPayload = buildCanonicalPayload(payload);

        String hashInput = prevHash + "|" + payload.getEventTime().toEpochMilli() + "|" +
                nextSequence + "|" + canonicalPayload;
        String recordHash = computeSha256Hex(hashInput);

        String merkleLeafHash = computeSha256Hex(recordHash);

        AnalyticsAuditLog auditLog = new AnalyticsAuditLog();
        auditLog.setCompanyId(payload.getCompanyId());
        auditLog.setEventTime(payload.getEventTime());
        auditLog.setEventDateUtc(eventDateUtc);
        auditLog.setEventType(payload.getEventType());
        auditLog.setEventSubtype(payload.getEventSubtype());
        auditLog.setPrincipalId(payload.getPrincipalId());
        auditLog.setPrincipalType(payload.getPrincipalType());
        auditLog.setObjectType(payload.getObjectType());
        auditLog.setObjectId(payload.getObjectId());
        auditLog.setIpAddress(payload.getIpAddress());
        auditLog.setUserAgent(payload.getUserAgent());
        auditLog.setRequestId(payload.getRequestId());
        auditLog.setRequestPath(payload.getRequestPath());
        auditLog.setRequestMethod(payload.getRequestMethod());
        auditLog.setResponseStatus(payload.getResponseStatus());
        auditLog.setMetadata(serializeMetadata(payload.getMetadata()));
        auditLog.setSequenceInCompany(nextSequence);
        auditLog.setSequenceInDay(sequenceInDay);
        auditLog.setPrevHash(prevHash);
        auditLog.setRecordHash(recordHash);
        auditLog.setMerkleLeafHash(merkleLeafHash);

        return auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditChainVerificationResult verifyDailyChain(Long companyId, LocalDate eventDateUtc) {
        List<AnalyticsAuditLog> records = auditLogRepository
                .findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(companyId, eventDateUtc);

        if (records.isEmpty()) {
            return AuditChainVerificationResult.builder()
                    .companyId(companyId)
                    .eventDateUtc(eventDateUtc)
                    .verified(false)
                    .checkpointCreated(false)
                    .status(AuditChainVerificationResult.STATUS_MISSING)
                    .mismatchReason("No records found for the specified date")
                    .build();
        }

        String expectedPrevHash = null;
        AnalyticsAuditLog firstRecord = records.get(0);
        if (firstRecord.getSequenceInCompany() > 1) {
            Optional<AnalyticsAuditLog> previousRecord = auditLogRepository
                    .findByCompanyIdAndSequenceInCompany(companyId, firstRecord.getSequenceInCompany() - 1);
            expectedPrevHash = previousRecord.map(AnalyticsAuditLog::getRecordHash).orElse(GENESIS_HASH);
        } else {
            expectedPrevHash = GENESIS_HASH;
        }

        for (AnalyticsAuditLog record : records) {
            if (!expectedPrevHash.equals(record.getPrevHash())) {
                return AuditChainVerificationResult.builder()
                        .companyId(companyId)
                        .eventDateUtc(eventDateUtc)
                        .verified(false)
                        .checkpointCreated(false)
                        .status(AuditChainVerificationResult.STATUS_MISMATCH)
                        .mismatchReason("Chain linkage broken at sequence " + record.getSequenceInCompany() +
                                ": expected prevHash=" + expectedPrevHash + ", got=" + record.getPrevHash())
                        .build();
            }

            String recomputedHash = recomputeRecordHash(record);
            if (!recomputedHash.equals(record.getRecordHash())) {
                return AuditChainVerificationResult.builder()
                        .companyId(companyId)
                        .eventDateUtc(eventDateUtc)
                        .verified(false)
                        .checkpointCreated(false)
                        .status(AuditChainVerificationResult.STATUS_MISMATCH)
                        .mismatchReason("Record hash mismatch at sequence " + record.getSequenceInCompany() +
                                ": expected=" + recomputedHash + ", stored=" + record.getRecordHash())
                        .build();
            }

            expectedPrevHash = record.getRecordHash();
        }

        List<String> leafHashes = records.stream()
                .map(AnalyticsAuditLog::getMerkleLeafHash)
                .toList();
        String computedMerkleRoot = computeMerkleRoot(leafHashes);

        Optional<AuditChainCheckpoint> existingCheckpoint = checkpointRepository
                .findByCompanyIdAndEventDateUtc(companyId, eventDateUtc);

        boolean checkpointCreated = existingCheckpoint.isEmpty();

        if (existingCheckpoint.isPresent()) {
            AuditChainCheckpoint checkpoint = existingCheckpoint.get();
            if (!computedMerkleRoot.equals(checkpoint.getMerkleRoot())) {
                return AuditChainVerificationResult.builder()
                        .companyId(companyId)
                        .eventDateUtc(eventDateUtc)
                        .verified(false)
                        .checkpointCreated(false)
                        .status(AuditChainVerificationResult.STATUS_MISMATCH)
                        .mismatchReason("Merkle root mismatch: computed=" + computedMerkleRoot +
                                ", stored=" + checkpoint.getMerkleRoot())
                        .build();
            }
        }

        return AuditChainVerificationResult.builder()
                .companyId(companyId)
                .eventDateUtc(eventDateUtc)
                .verified(true)
                .checkpointCreated(checkpointCreated)
                .status(AuditChainVerificationResult.STATUS_OK)
                .build();
    }

    @Override
    public void recomputeAndStoreDailyCheckpoint(Long companyId, LocalDate eventDateUtc) {
        List<AnalyticsAuditLog> records = auditLogRepository
                .findByCompanyIdAndEventDateUtcOrderBySequenceInCompanyAsc(companyId, eventDateUtc);

        if (records.isEmpty()) {
            log.warn("No records found for company {} on date {}", companyId, eventDateUtc);
            return;
        }

        List<String> leafHashes = records.stream()
                .map(AnalyticsAuditLog::getMerkleLeafHash)
                .toList();
        String merkleRoot = computeMerkleRoot(leafHashes);

        AnalyticsAuditLog firstRecord = records.get(0);
        AnalyticsAuditLog lastRecord = records.get(records.size() - 1);

        AuditChainCheckpoint checkpoint = checkpointRepository
                .findByCompanyIdAndEventDateUtc(companyId, eventDateUtc)
                .orElseGet(() -> {
                    AuditChainCheckpoint newCheckpoint = new AuditChainCheckpoint();
                    newCheckpoint.setCompanyId(companyId);
                    newCheckpoint.setEventDateUtc(eventDateUtc);
                    return newCheckpoint;
                });

        checkpoint.setFirstSequence(firstRecord.getSequenceInCompany());
        checkpoint.setLastSequence(lastRecord.getSequenceInCompany());
        checkpoint.setRecordCount((long) records.size());
        checkpoint.setMerkleRoot(merkleRoot);
        checkpoint.setChainHeadHash(firstRecord.getPrevHash());
        checkpoint.setChainTailHash(lastRecord.getRecordHash());
        checkpoint.setStatus(AuditChainVerificationResult.STATUS_OK);
        checkpoint.setLastVerifiedAt(Instant.now());
        checkpoint.setVerificationError(null);

        checkpointRepository.save(checkpoint);

        log.info("Stored checkpoint for company {} date {}: merkleRoot={}, records={}",
                companyId, eventDateUtc, merkleRoot, records.size());
    }

    @Override
    public String computeMerkleRoot(List<String> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            return computeSha256Hex("");
        }

        if (leafHashes.size() == 1) {
            return leafHashes.get(0);
        }

        List<String> currentLevel = new ArrayList<>(leafHashes);

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();

            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right;
                if (i + 1 < currentLevel.size()) {
                    right = currentLevel.get(i + 1);
                } else {
                    right = left;
                }
                String combined = computeSha256Hex(left + right);
                nextLevel.add(combined);
            }

            currentLevel = nextLevel;
        }

        return currentLevel.get(0);
    }

    private String buildCanonicalPayload(AuditLogPayload payload) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("companyId", payload.getCompanyId());
        canonical.put("eventTime", payload.getEventTime() != null ? payload.getEventTime().toString() : null);
        canonical.put("eventType", payload.getEventType());
        canonical.put("eventSubtype", payload.getEventSubtype());
        canonical.put("principalId", payload.getPrincipalId());
        canonical.put("principalType", payload.getPrincipalType());
        canonical.put("objectType", payload.getObjectType());
        canonical.put("objectId", payload.getObjectId());
        canonical.put("ipAddress", payload.getIpAddress());
        canonical.put("requestId", payload.getRequestId() != null ? payload.getRequestId().toString() : null);
        canonical.put("requestPath", payload.getRequestPath());
        canonical.put("requestMethod", payload.getRequestMethod());
        canonical.put("responseStatus", payload.getResponseStatus());
        if (payload.getMetadata() != null) {
            canonical.put("metadata", new TreeMap<>(payload.getMetadata()));
        }

        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize canonical payload", e);
            return "{}";
        }
    }

    private String recomputeRecordHash(AnalyticsAuditLog record) {
        AuditLogPayload payload = AuditLogPayload.builder()
                .companyId(record.getCompanyId())
                .eventTime(record.getEventTime())
                .eventType(record.getEventType())
                .eventSubtype(record.getEventSubtype())
                .principalId(record.getPrincipalId())
                .principalType(record.getPrincipalType())
                .objectType(record.getObjectType())
                .objectId(record.getObjectId())
                .ipAddress(record.getIpAddress())
                .requestId(record.getRequestId())
                .requestPath(record.getRequestPath())
                .requestMethod(record.getRequestMethod())
                .responseStatus(record.getResponseStatus())
                .metadata(deserializeMetadata(record.getMetadata()))
                .build();

        String canonicalPayload = buildCanonicalPayload(payload);
        String hashInput = record.getPrevHash() + "|" + record.getEventTime().toEpochMilli() + "|" +
                record.getSequenceInCompany() + "|" + canonicalPayload;
        return computeSha256Hex(hashInput);
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
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize metadata", e);
            return null;
        }
    }
}
