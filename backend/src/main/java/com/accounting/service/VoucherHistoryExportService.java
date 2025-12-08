package com.accounting.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.accounting.dto.VoucherHistoryEntryDTO;

/**
 * Service for exporting voucher history in various formats (PDF, JSON).
 */
@Service
public class VoucherHistoryExportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * Export voucher history as JSON with cryptographic hashes.
     *
     * @param voucherId voucher ID
     * @param history   list of history entries
     * @return JSON string
     */
    public String exportAsJson(UUID voucherId, List<VoucherHistoryEntryDTO> history) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            com.fasterxml.jackson.databind.node.ObjectNode root = mapper.createObjectNode();
            root.put("voucherId", voucherId.toString());
            root.put("exportedAt", Instant.now().toString());
            root.put("format", "json");
            root.put("count", history.size());

            com.fasterxml.jackson.databind.node.ArrayNode historyArray = mapper.createArrayNode();
            for (VoucherHistoryEntryDTO entry : history) {
                com.fasterxml.jackson.databind.node.ObjectNode entryNode = mapper.valueToTree(entry);
                historyArray.add(entryNode);
            }
            root.set("history", historyArray);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export voucher history as JSON", e);
        }
    }

    /**
     * Export voucher history as PDF with hash watermark.
     * For MVP, generates a text-based document that can be converted to PDF.
     * In production, this should use a proper PDF library like Apache PDFBox or
     * iText.
     *
     * @param voucherId voucher ID
     * @param history   list of history entries
     * @return PDF byte array (text-based for MVP)
     */
    public byte[] exportAsPdf(UUID voucherId, List<VoucherHistoryEntryDTO> history) throws IOException {
        // For MVP, generate a structured text document
        // In production, integrate with PDFBox or iText for proper PDF generation
        return exportAsPdfSimple(voucherId, history);
    }

    /**
     * Simple PDF export (text-based for MVP).
     * Generates a structured text document with hash watermark.
     * TODO: Integrate with PDFBox or iText for proper PDF generation in production.
     */
    private byte[] exportAsPdfSimple(UUID voucherId, List<VoucherHistoryEntryDTO> history) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("VOUCHER HISTORY REPORT\n");
        sb.append("========================================\n\n");
        sb.append("Voucher ID: ").append(voucherId.toString()).append("\n");
        sb.append("Exported At: ").append(DATE_FORMATTER.format(Instant.now())).append("\n");
        sb.append("Total Entries: ").append(history.size()).append("\n");
        sb.append("========================================\n\n");

        int entryNumber = 1;
        for (VoucherHistoryEntryDTO entry : history) {
            sb.append("Entry #").append(entryNumber++).append("\n");
            sb.append("----------------------------------------\n");
            sb.append("Action: ").append(entry.getAction()).append("\n");
            sb.append("Summary: ").append(entry.getSummary()).append("\n");
            sb.append("Timestamp: ").append(DATE_FORMATTER.format(entry.getTimestamp())).append("\n");

            if (entry.getUserEmail() != null) {
                sb.append("User: ").append(entry.getUserEmail()).append("\n");
            }
            if (entry.getUserRole() != null) {
                sb.append("Role: ").append(entry.getUserRole()).append("\n");
            }
            if (entry.getIpAddress() != null) {
                sb.append("IP Address: ").append(entry.getIpAddress()).append("\n");
            }
            if (entry.getDiffHash() != null) {
                sb.append("Change Hash: ").append(entry.getDiffHash()).append("\n");
            }
            if (entry.getSuccess() != null) {
                sb.append("Status: ").append(entry.getSuccess() ? "Success" : "Failed").append("\n");
            }
            if (entry.getFailureReason() != null) {
                sb.append("Failure Reason: ").append(entry.getFailureReason()).append("\n");
            }

            // Add field-level diff if available
            if (entry.getDiff() != null && !entry.getDiff().isEmpty()) {
                sb.append("\nField Changes:\n");
                entry.getDiff().forEach((field, diff) -> {
                    sb.append("  - ").append(field).append(": ");
                    if (diff.getChangeType() == com.accounting.service.VoucherHistoryService.ChangeType.ADDED) {
                        sb.append("ADDED (").append(diff.getAfterValue()).append(")\n");
                    } else if (diff
                            .getChangeType() == com.accounting.service.VoucherHistoryService.ChangeType.REMOVED) {
                        sb.append("REMOVED (").append(diff.getBeforeValue()).append(")\n");
                    } else {
                        sb.append("CHANGED (")
                                .append(diff.getBeforeValue())
                                .append(" -> ")
                                .append(diff.getAfterValue())
                                .append(")\n");
                    }
                });
            }

            sb.append("\n");
        }

        // Calculate hash of document content
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        String hash = calculateSha256Hash(content);

        // Append hash watermark
        sb.append("========================================\n");
        sb.append("DOCUMENT INTEGRITY VERIFICATION\n");
        sb.append("========================================\n");
        sb.append("Document Hash (SHA-256):\n");
        sb.append(hash).append("\n");
        sb.append("========================================\n");
        sb.append("This hash can be used to verify the integrity\n");
        sb.append("and authenticity of this audit trail report.\n");
        sb.append("========================================\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Calculate SHA-256 hash of byte array.
     */
    private String calculateSha256Hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
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
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
