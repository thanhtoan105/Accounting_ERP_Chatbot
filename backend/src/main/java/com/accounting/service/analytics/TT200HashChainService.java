package com.accounting.service.analytics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.accounting.entity.analytics.AnalyticsAuditLog;

@Service
public class TT200HashChainService {

    private static final Logger log = LoggerFactory.getLogger(TT200HashChainService.class);
    private static final String SHA_256 = "SHA-256";

    public String computeRecordHash(AnalyticsAuditLog auditLog) {
        StringBuilder content = new StringBuilder();
        content.append(auditLog.getCompanyId() != null ? auditLog.getCompanyId() : "");
        content.append("|");
        content.append(auditLog.getEventTime() != null ? auditLog.getEventTime().toString() : "");
        content.append("|");
        content.append(auditLog.getEventDateUtc() != null ? auditLog.getEventDateUtc().toString() : "");
        content.append("|");
        content.append(auditLog.getEventType() != null ? auditLog.getEventType() : "");
        content.append("|");
        content.append(auditLog.getEventSubtype() != null ? auditLog.getEventSubtype() : "");
        content.append("|");
        content.append(auditLog.getPrincipalId() != null ? auditLog.getPrincipalId() : "");
        content.append("|");
        content.append(auditLog.getPrincipalType() != null ? auditLog.getPrincipalType() : "");
        content.append("|");
        content.append(auditLog.getObjectType() != null ? auditLog.getObjectType() : "");
        content.append("|");
        content.append(auditLog.getObjectId() != null ? auditLog.getObjectId() : "");
        content.append("|");
        content.append(auditLog.getIpAddress() != null ? auditLog.getIpAddress() : "");
        content.append("|");
        content.append(auditLog.getRequestPath() != null ? auditLog.getRequestPath() : "");
        content.append("|");
        content.append(auditLog.getRequestMethod() != null ? auditLog.getRequestMethod() : "");
        content.append("|");
        content.append(auditLog.getResponseStatus() != null ? auditLog.getResponseStatus() : "");
        content.append("|");
        content.append(auditLog.getMetadata() != null ? auditLog.getMetadata() : "");
        content.append("|");
        content.append(auditLog.getSequenceInCompany() != null ? auditLog.getSequenceInCompany() : "");
        content.append("|");
        content.append(auditLog.getSequenceInDay() != null ? auditLog.getSequenceInDay() : "");
        content.append("|");
        content.append(auditLog.getPrevHash() != null ? auditLog.getPrevHash() : "");

        return sha256Hex(content.toString());
    }

    public String computeMerkleLeafHash(String recordHash) {
        return sha256Hex(recordHash);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
