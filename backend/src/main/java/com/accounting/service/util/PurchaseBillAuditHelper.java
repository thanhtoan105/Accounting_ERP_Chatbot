package com.accounting.service.util;

import com.accounting.entity.PurchaseBill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for purchase bill audit operations.
 * Provides methods for serializing purchase bills to JSON snapshots and calculating SHA-256 diff hashes.
 */
@Component
public class PurchaseBillAuditHelper {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseBillAuditHelper.class);
    private final ObjectMapper objectMapper;

    public PurchaseBillAuditHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialize purchase bill entity to JSON snapshot.
     *
     * @param purchaseBill purchase bill entity to serialize
     * @return JSON snapshot of the purchase bill
     */
    public JsonNode serializePurchaseBillToJson(PurchaseBill purchaseBill) {
        try {
            return objectMapper.valueToTree(purchaseBill);
        } catch (Exception e) {
            logger.error("Failed to serialize purchase bill to JSON: {}", e.getMessage(), e);
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
}

