package com.accounting.service.util;

import com.accounting.entity.Voucher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for voucher audit operations.
 * Provides methods for serializing vouchers to JSON snapshots and calculating SHA-256 diff hashes.
 */
@Component
public class VoucherAuditHelper {

    private static final Logger logger = LoggerFactory.getLogger(VoucherAuditHelper.class);
    private final ObjectMapper objectMapper;

    public VoucherAuditHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialize voucher entity to JSON snapshot.
     *
     * @param voucher voucher entity to serialize
     * @return JSON snapshot of the voucher
     */
    public JsonNode serializeVoucherToJson(Voucher voucher) {
        try {
            return objectMapper.valueToTree(voucher);
        } catch (Exception e) {
            logger.error("Failed to serialize voucher to JSON: {}", e.getMessage(), e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Calculate SHA-256 hash of the JSON diff between before and after snapshots.
     * The diff is calculated as the JSON representation of the difference between before and after.
     *
     * @param beforeSnapshot JSON snapshot before the change (null for create)
     * @param afterSnapshot  JSON snapshot after the change
     * @return SHA-256 hash of the diff (hex string)
     */
    public String calculateDiffHash(JsonNode beforeSnapshot, JsonNode afterSnapshot) {
        try {
            // Create diff object
            com.fasterxml.jackson.databind.node.ObjectNode diff = objectMapper.createObjectNode();
            if (beforeSnapshot != null) {
                diff.set("before", beforeSnapshot);
            }
            if (afterSnapshot != null) {
                diff.set("after", afterSnapshot);
            }

            // Serialize diff to JSON string
            String diffJson = objectMapper.writeValueAsString(diff);

            // Calculate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(diffJson.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Failed to calculate diff hash: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Serialize object to JSON string.
     *
     * @param object object to serialize
     * @return JSON string
     */
    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            logger.error("Failed to serialize object to JSON: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * Calculate SHA-256 hash of a string.
     *
     * @param input string to hash
     * @return SHA-256 hash (hex string)
     */
    public String calculateSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 algorithm not available: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Failed to calculate SHA-256 hash: {}", e.getMessage(), e);
            return null;
        }
    }
}

