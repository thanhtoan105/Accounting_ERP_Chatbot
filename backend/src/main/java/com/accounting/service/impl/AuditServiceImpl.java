package com.accounting.service.impl;

import com.accounting.entity.AuditLog;
import com.accounting.entity.ImportAuditEntry;
import com.accounting.entity.User;
import com.accounting.imports.ImportType;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.ImportAuditEntryRepository;
import com.accounting.service.AuditService;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AuditServiceImpl implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ImportAuditEntryRepository importAuditEntryRepository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            ImportAuditEntryRepository importAuditEntryRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.importAuditEntryRepository = importAuditEntryRepository;
        this.objectMapper = objectMapper;
    }

    private AuditLog startLog(String action, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEventType(resolveEventType(action));
        log.setCompanyId(CompanyContext.getCompanyId());
        log.setCreatedAt(Instant.now());
        log.setSuccess(Boolean.TRUE);
        if (request != null) {
            log.setIpAddress(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
            String traceId = resolveTraceId(request);
            if (traceId != null) {
                log.setTraceId(traceId);
            }
        }
        return log;
    }

    private void assignActor(AuditLog log, User user) {
        if (user == null) {
            return;
        }
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        if (StringUtils.hasText(user.getRole())) {
            log.setActorRole(user.getRole());
        }
    }

    private void assignActor(AuditLog log, Long userId, String fallbackEmail) {
        if (userId != null) {
            log.setUserId(userId);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                log.setEmail(user.getEmail());
                if (StringUtils.hasText(user.getRole())) {
                    log.setActorRole(user.getRole());
                }
                return;
            }
        }
        if (StringUtils.hasText(fallbackEmail) && !StringUtils.hasText(log.getEmail())) {
            log.setEmail(fallbackEmail);
        }
    }

    private void assignEntity(AuditLog log, String entityType, Object entityId, String display) {
        log.setEntityType(entityType);
        if (entityId != null) {
            log.setEntityId(entityId.toString());
        }
        if (StringUtils.hasText(display)) {
            log.setEntityDisplay(display);
        }
    }

    private ObjectNode buildChangePayload(Map<String, String> oldValues, Map<String, String> newValues) {
        ObjectNode root = objectMapper.createObjectNode();
        if (oldValues != null && !oldValues.isEmpty()) {
            ObjectNode before = objectMapper.createObjectNode();
            oldValues.forEach(before::put);
            root.set("before", before);
        }
        if (newValues != null && !newValues.isEmpty()) {
            ObjectNode after = objectMapper.createObjectNode();
            newValues.forEach(after::put);
            root.set("after", after);
        }
        return root.isEmpty() ? null : root;
    }

    private ObjectNode buildMetadata() {
        return objectMapper.createObjectNode();
    }

    private String resolveEventType(String action) {
        if (!StringUtils.hasText(action)) {
            return "GENERAL";
        }
        if (action.startsWith("CUSTOMER") || action.startsWith("SUPPLIER") || action.startsWith("BANK_ACCOUNT")
                || action.startsWith("COMPANY_SETTINGS") || action.startsWith("DATA_INTEGRITY")) {
            return "MASTER_DATA";
        }
        if (action.startsWith("LOGIN") || action.startsWith("PASSWORD") || action.startsWith("ROLE")
                || action.startsWith("INVITATION") || action.startsWith("AUDIT_USER")) {
            return "SECURITY";
        }
        if (action.startsWith("REPORT") || action.startsWith("AUDIT_EXPORT")) {
            return "COMPLIANCE";
        }
        return "GENERAL";
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String traceId = request.getHeader("X-Request-Id");
        if (!StringUtils.hasText(traceId)) {
            traceId = request.getHeader("X-Correlation-Id");
        }
        return StringUtils.hasText(traceId) ? traceId : null;
    }

    @SuppressWarnings("null")
    private void persist(AuditLog log) {
        // Calculate chain hash before saving
        try {
            if (log.getCompanyId() != null) {
                Optional<AuditLog> previousLog = auditLogRepository
                        .findFirstByCompanyIdOrderByCreatedAtDesc(log.getCompanyId());

                String previousHash = previousLog.map(AuditLog::getChainHash).orElse("GENESIS");

                // Calculate hash of current log content
                String content = log.getAction() +
                        (log.getEntityId() != null ? log.getEntityId() : "") +
                        log.getCreatedAt().toString() +
                        (log.getUserId() != null ? log.getUserId().toString() : "") +
                        (log.getMetadata() != null ? log.getMetadata().toString() : "");

                String currentHash = calculateSha256(previousHash + content);
                log.setChainHash(currentHash);
            }
        } catch (Exception e) {
            logger.error("Failed to calculate chain hash", e);
            log.setChainHash("HASH_CALC_FAILED");
        }

        auditLogRepository.save(log);
    }

    private String calculateSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private String trimReason(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() > 50 ? value.substring(0, 47) + "..." : value;
    }

    @Override
    public void logLoginSuccess(User user, HttpServletRequest request) {
        AuditLog log = startLog("LOGIN_SUCCESS", request);
        assignActor(log, user);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logLoginFailure(User user, String email, String reason, HttpServletRequest request) {
        AuditLog log = startLog("LOGIN_FAILURE", request);
        if (user != null) {
            assignActor(log, user);
        } else {
            assignActor(log, (Long) null, email);
        }
        log.setFailureReason(reason);
        log.setSuccess(Boolean.FALSE);
        persist(log);
    }

    @Override
    public void logPasswordResetRequest(User user, HttpServletRequest request) {
        AuditLog log = startLog("PASSWORD_RESET_REQUESTED", request);
        assignActor(log, user);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logPasswordResetCompleted(User user, HttpServletRequest request) {
        AuditLog log = startLog("PASSWORD_RESET_COMPLETED", request);
        assignActor(log, user);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logRoleChange(
            User targetUser, String oldRole, String newRole, Long changedByUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(targetUser.getId()); // target_user_id
        log.setEmail(targetUser.getEmail());
        log.setAction("ROLE_CHANGED");
        // Store role change details in reason field: format
        // "old_role:{old},new_role:{new},changed_by:{userId}"
        // Note: In production, this should be separate fields (old_role, new_role,
        // changed_by_user_id)
        // but requires schema migration. Using reason field for now.
        // Truncate to fit VARCHAR(50) constraint
        String reason = String.format("old_role:%s,new_role:%s,changed_by:%d", oldRole, newRole, changedByUserId);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logInvitationCreated(
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            String role,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(inviterUserId); // inviter user ID
        log.setEmail(inviteeEmail); // invitee email
        log.setAction("INVITATION_CREATED");
        // Store invitation details in reason field: format
        // "inviter:{userId},invitee:{email},company:{companyId},role:{role},status:{status}"
        // Truncate to fit VARCHAR(50) constraint
        String reason = String.format(
                "inviter:%d,invitee:%s,company:%d,role:%s,status:PENDING",
                inviterUserId, inviteeEmail, companyId, role);
        if (reason.length() > 50) {
            // Truncate email if needed, keeping essential info
            String truncatedEmail = inviteeEmail.length() > 15 ? inviteeEmail.substring(0, 15) + "..." : inviteeEmail;
            reason = String.format("inviter:%d,inv:%s,c:%d,r:%s,P", inviterUserId, truncatedEmail, companyId, role);
            if (reason.length() > 50) {
                reason = reason.substring(0, 47) + "...";
            }
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logInvitationAccepted(
            Long invitationId,
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        // For acceptance, userId could be null if user doesn't exist yet in audit log
        // Use inviterUserId for reference, inviteeEmail identifies the user being
        // accepted
        log.setUserId(inviterUserId); // inviter user ID
        log.setEmail(inviteeEmail); // invitee email
        log.setAction("INVITATION_ACCEPTED");
        // Store invitation acceptance details in reason field (truncate to fit
        // VARCHAR(50))
        String reason = String.format(
                "inv_id:%d,inviter:%d,inv:%s,c:%d,ACC",
                invitationId, inviterUserId, inviteeEmail.length() > 10 ? inviteeEmail.substring(0, 10) : inviteeEmail,
                companyId);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logInvitationExpired(
            Long invitationId,
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(inviterUserId); // inviter user ID
        log.setEmail(inviteeEmail); // invitee email
        log.setAction("INVITATION_EXPIRED");
        // Store invitation expiration details in reason field (truncate to fit
        // VARCHAR(50))
        String reason = String.format(
                "inv_id:%d,inviter:%d,inv:%s,c:%d,EXP",
                invitationId, inviterUserId, inviteeEmail.length() > 10 ? inviteeEmail.substring(0, 10) : inviteeEmail,
                companyId);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logInvitationCancelled(
            Long invitationId,
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(inviterUserId); // inviter/canceller user ID
        log.setEmail(inviteeEmail); // invitee email
        log.setAction("INVITATION_CANCELLED");
        // Store invitation cancellation details in reason field (truncate to fit
        // VARCHAR(50))
        String reason = String.format(
                "inv_id:%d,inviter:%d,inv:%s,c:%d,CAN",
                invitationId, inviterUserId, inviteeEmail.length() > 10 ? inviteeEmail.substring(0, 10) : inviteeEmail,
                companyId);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logUserCreated(User user, Long createdByUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("AUDIT_USER_CREATED");
        // Store creator and user details in reason field (truncate to fit VARCHAR(50))
        String reason = String.format("created_by:%d,email:%s,role:%s", createdByUserId,
                user.getEmail().length() > 15 ? user.getEmail().substring(0, 15) : user.getEmail(), user.getRole());
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logUserUpdated(
            User user,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("AUDIT_USER_UPDATED");
        // Store update details in reason field (truncate to fit VARCHAR(50))
        // Format: updated_by:{userId},changes:{field1:old->new,field2:old->new}
        StringBuilder changes = new StringBuilder();
        for (String field : newValues.keySet()) {
            String oldVal = oldValues.getOrDefault(field, "");
            String newVal = newValues.get(field);
            if (changes.length() > 0)
                changes.append(",");
            changes.append(field).append(":").append(oldVal).append("->").append(newVal);
        }
        String reason = String.format("by:%d,%s", updatedByUserId, changes.toString());
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logUserDeactivated(User user, Long deactivatedByUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("AUDIT_USER_DEACTIVATED");
        String reason = String.format("deactivated_by:%d", deactivatedByUserId);
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logUserActivated(User user, Long activatedByUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("AUDIT_USER_ACTIVATED");
        String reason = String.format("activated_by:%d", activatedByUserId);
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logPasswordResetByAdmin(User targetUser, Long adminUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(targetUser.getId());
        log.setEmail(targetUser.getEmail());
        log.setAction("AUDIT_PASSWORD_RESET_ADMIN");
        String reason = String.format("admin:%d,target:%d", adminUserId, targetUser.getId());
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logProfileUpdated(User user, java.util.Map<String, String> updatedFields, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("AUDIT_PROFILE_UPDATED");
        // Store updated fields in reason field (truncate to fit VARCHAR(50))
        StringBuilder fields = new StringBuilder();
        for (String field : updatedFields.keySet()) {
            if (fields.length() > 0)
                fields.append(",");
            fields.append(field).append(":").append(updatedFields.get(field));
        }
        String reason = fields.toString();
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logPasswordChanged(User user, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("AUDIT_PASSWORD_CHANGED");
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logVoucherDeleted(
            java.util.UUID voucherId,
            String voucherNumber,
            String reason,
            Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deletedByUserId);
        // Get user email if available
        if (deletedByUserId != null) {
            userRepository
                    .findById(deletedByUserId)
                    .ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("VOUCHER_DELETED");
        // Store voucher ID, number, and reason (truncate to fit VARCHAR(50))
        String reasonText = String.format("voucher:%s,number:%s", voucherId, voucherNumber);
        if (reason != null && !reason.isBlank()) {
            // Include reason, but truncate if too long
            String fullReason = reasonText + ",reason:" + reason;
            if (fullReason.length() > 50) {
                reasonText = reasonText + ",reason:" + reason.substring(0, Math.min(reason.length(), 30)) + "...";
            } else {
                reasonText = fullReason;
            }
        }
        log.setReason(reasonText.length() > 50 ? reasonText.substring(0, 47) + "..." : reasonText);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logVoucherPosted(
            java.util.UUID voucherId,
            String voucherNumber,
            Long postedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(postedByUserId);
        // Get user email if available
        if (postedByUserId != null) {
            userRepository
                    .findById(postedByUserId)
                    .ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("VOUCHER_POSTED");
        String reasonText = String.format("voucher:%s,number:%s", voucherId, voucherNumber);
        log.setReason(reasonText.length() > 50 ? reasonText.substring(0, 47) + "..." : reasonText);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logVoucherUnposted(
            java.util.UUID voucherId,
            String voucherNumber,
            String reason,
            Long unpostedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(unpostedByUserId);
        // Get user email if available
        if (unpostedByUserId != null) {
            userRepository
                    .findById(unpostedByUserId)
                    .ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("VOUCHER_UNPOSTED");
        // Store voucher ID, number, and reason (truncate to fit VARCHAR(50))
        String reasonText = String.format("voucher:%s,number:%s", voucherId, voucherNumber);
        if (reason != null && !reason.isBlank()) {
            // Include reason, but truncate if too long
            String fullReason = reasonText + ",reason:" + reason;
            if (fullReason.length() > 50) {
                reasonText = reasonText + ",reason:" + reason.substring(0, Math.min(reason.length(), 30)) + "...";
            } else {
                reasonText = fullReason;
            }
        }
        log.setReason(reasonText.length() > 50 ? reasonText.substring(0, 47) + "..." : reasonText);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logVoucherReversed(
            java.util.UUID originalVoucherId,
            String originalVoucherNumber,
            java.util.UUID reversalVoucherId,
            String reversalVoucherNumber,
            String reason,
            Long reversedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(reversedByUserId);
        // Get user email if available
        if (reversedByUserId != null) {
            userRepository
                    .findById(reversedByUserId)
                    .ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("VOUCHER_REVERSED");
        // Store original and reversal voucher IDs/numbers, and reason (truncate to fit
        // VARCHAR(50))
        String reasonText = String.format("original:%s,reversal:%s", originalVoucherId, reversalVoucherId);
        if (reason != null && !reason.isBlank()) {
            // Include reason, but truncate if too long
            String fullReason = reasonText + ",reason:" + reason;
            if (fullReason.length() > 50) {
                reasonText = reasonText + ",reason:" + reason.substring(0, Math.min(reason.length(), 20)) + "...";
            } else {
                reasonText = fullReason;
            }
        }
        log.setReason(reasonText.length() > 50 ? reasonText.substring(0, 47) + "..." : reasonText);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCompanySettingsUpdated(
            Long companyId,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = startLog("COMPANY_SETTINGS_UPDATED", request);
        assignActor(log, updatedByUserId, null);
        assignEntity(log, "COMPANY", companyId, companyId != null ? companyId.toString() : null);
        ObjectNode metadata = buildMetadata();
        metadata.put("companyId", companyId != null ? companyId : -1);
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(oldValues, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logReportExport(Long companyId, Long userId, String format, HttpServletRequest request) {
        AuditLog log = startLog("REPORT_EXPORT", request);
        assignActor(log, userId, null);
        assignEntity(log, "COMPANY", companyId, companyId != null ? companyId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        metadata.put("format", format);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logAgingReportViewed(Long companyId, Long userId, java.util.Map<String, Object> filters) {
        AuditLog log = startLog("AGING_REPORT_VIEWED", null);
        assignActor(log, userId, null);
        assignEntity(log, "COMPANY", companyId, companyId != null ? companyId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (filters != null && !filters.isEmpty()) {
            metadata.set("filters", objectMapper.valueToTree(filters));
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logAgingDrilldownViewed(Long companyId, Long userId, Long supplierId, String bucket) {
        AuditLog log = startLog("AGING_DRILLDOWN_VIEWED", null);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierId != null ? supplierId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (bucket != null) {
            metadata.put("bucket", bucket);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logVatReportGenerated(
            Long companyId,
            Long userId,
            UUID reportId,
            String reportType,
            Map<String, Object> filters) {
        AuditLog log = startLog("VAT_REPORT_GENERATED", null);
        assignActor(log, userId, null);
        assignEntity(log, "VAT_REPORT", reportId, reportId != null ? reportId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (reportType != null) {
            metadata.put("reportType", reportType);
        }
        if (filters != null && !filters.isEmpty()) {
            metadata.set("filters", objectMapper.valueToTree(filters));
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logVatCorrectionCreated(
            Long companyId,
            Long userId,
            UUID correctionId,
            UUID billId,
            BigDecimal oldAmount,
            BigDecimal newAmount,
            String reason) {
        AuditLog log = startLog("VAT_CORRECTION_CREATED", null);
        assignActor(log, userId, null);
        assignEntity(log, "VAT_CORRECTION", correctionId, correctionId != null ? correctionId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (billId != null) {
            metadata.put("purchaseBillId", billId.toString());
        }
        metadata.put("oldVatAmount", oldAmount != null ? oldAmount.toPlainString() : "0");
        metadata.put("newVatAmount", newAmount != null ? newAmount.toPlainString() : "0");
        if (reason != null) {
            metadata.put("reason", reason);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logVatCorrectionApproved(
            Long companyId,
            Long userId,
            UUID correctionId,
            UUID billId,
            BigDecimal oldAmount,
            BigDecimal newAmount) {
        AuditLog log = startLog("VAT_CORRECTION_APPROVED", null);
        assignActor(log, userId, null);
        assignEntity(log, "VAT_CORRECTION", correctionId, correctionId != null ? correctionId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (billId != null) {
            metadata.put("purchaseBillId", billId.toString());
        }
        metadata.put("oldVatAmount", oldAmount != null ? oldAmount.toPlainString() : "0");
        metadata.put("newVatAmount", newAmount != null ? newAmount.toPlainString() : "0");
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logVatRateOverride(
            Long companyId,
            Long userId,
            String actualRate,
            String defaultRate,
            HttpServletRequest request) {
        AuditLog log = startLog("VAT_RATE_OVERRIDE", request);
        assignActor(log, userId, null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (actualRate != null) {
            metadata.put("actualRate", actualRate);
        }
        if (defaultRate != null) {
            metadata.put("defaultRate", defaultRate);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logVatSumValidationFailure(
            Long companyId,
            Long userId,
            UUID billId,
            BigDecimal lineVATSum,
            BigDecimal documentVAT,
            BigDecimal difference,
            HttpServletRequest request) {
        AuditLog log = startLog("VAT_SUM_VALIDATION_FAILURE", request);
        assignActor(log, userId, null);
        if (billId != null) {
            assignEntity(log, "PURCHASE_BILL", billId, billId.toString());
        }
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (lineVATSum != null) {
            metadata.put("lineVATSum", lineVATSum.toPlainString());
        }
        if (documentVAT != null) {
            metadata.put("documentVAT", documentVAT.toPlainString());
        }
        if (difference != null) {
            metadata.put("difference", difference.toPlainString());
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.FALSE); // Validation failure
        persist(log);
    }

    @Override
    public void logVatRatioBlock(
            Long companyId,
            Long userId,
            BigDecimal amount,
            BigDecimal vatAmount,
            BigDecimal ratio,
            String reason,
            HttpServletRequest request) {
        AuditLog log = startLog("VAT_RATIO_BLOCK", request);
        assignActor(log, userId, null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (amount != null) {
            metadata.put("amount", amount.toPlainString());
        }
        if (vatAmount != null) {
            metadata.put("vatAmount", vatAmount.toPlainString());
        }
        if (ratio != null) {
            metadata.put("ratio", ratio.toPlainString());
        }
        if (reason != null) {
            metadata.put("reason", reason);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.FALSE); // Blocked attempt
        persist(log);
    }

    @Override
    public void logStatementGenerated(
            Long companyId,
            Long userId,
            java.util.UUID statementId,
            Long supplierId,
            String statementType,
            HttpServletRequest request) {
        AuditLog log = startLog("STATEMENT_GENERATED", request);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER_STATEMENT", statementId, statementId != null ? statementId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (supplierId != null) {
            metadata.put("supplierId", supplierId);
        }
        if (statementType != null) {
            metadata.put("statementType", statementType);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logStatementExported(
            Long companyId,
            Long userId,
            java.util.UUID statementId,
            String format,
            HttpServletRequest request) {
        AuditLog log = startLog("STATEMENT_EXPORTED", request);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER_STATEMENT", statementId, statementId != null ? statementId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (format != null) {
            metadata.put("format", format);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logStatementSent(
            Long companyId,
            Long userId,
            java.util.UUID statementId,
            int recipientCount,
            HttpServletRequest request) {
        AuditLog log = startLog("STATEMENT_SENT", request);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER_STATEMENT", statementId, statementId != null ? statementId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        metadata.put("recipientCount", recipientCount);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logStatementImported(
            Long companyId,
            Long userId,
            Long supplierId,
            int itemCount,
            int mismatchCount,
            HttpServletRequest request) {
        AuditLog log = startLog("STATEMENT_IMPORTED", request);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierId != null ? supplierId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        metadata.put("itemCount", itemCount);
        metadata.put("mismatchCount", mismatchCount);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logReconciliationSaved(
            Long companyId,
            Long userId,
            Long supplierId,
            int disputeCount,
            HttpServletRequest request) {
        AuditLog log = startLog("RECONCILIATION_SAVED", request);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierId != null ? supplierId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        metadata.put("disputeCount", disputeCount);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logDisputeUpdated(
            Long companyId,
            Long userId,
            java.util.UUID disputeId,
            String oldStatus,
            String newStatus,
            HttpServletRequest request) {
        AuditLog log = startLog("DISPUTE_UPDATED", request);
        assignActor(log, userId, null);
        assignEntity(log, "SUPPLIER_STATEMENT_DISPUTE", disputeId, disputeId != null ? disputeId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (companyId != null) {
            metadata.put("companyId", companyId);
        }
        if (oldStatus != null) {
            metadata.put("oldStatus", oldStatus);
        }
        if (newStatus != null) {
            metadata.put("newStatus", newStatus);
        }
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logCustomerCreated(
            Long customerId,
            String customerCode,
            Long createdByUserId,
            Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_CREATED", request);
        assignActor(log, createdByUserId, null);
        assignEntity(log, "CUSTOMER", customerId, customerCode);
        ObjectNode metadata = buildMetadata();
        metadata.put("code", customerCode);
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(null, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logCustomerUpdated(Long customerId, String customerCode, Long updatedByUserId,
            java.util.Map<String, String> oldValues, java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_UPDATED", request);
        assignActor(log, updatedByUserId, null);
        assignEntity(log, "CUSTOMER", customerId, customerCode);
        ObjectNode metadata = buildMetadata();
        metadata.put("code", customerCode);
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(oldValues, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logCustomerDeleted(Long customerId, String customerCode, String reason, Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_DELETED", request);
        assignActor(log, deletedByUserId, null);
        assignEntity(log, "CUSTOMER", customerId, customerCode);
        ObjectNode metadata = buildMetadata();
        metadata.put("code", customerCode);
        metadata.put("reason", reason);
        log.setMetadata(metadata);
        log.setReason(trimReason(String.format("id:%s,code:%s", customerId, customerCode)));
        log.setSuccess(Boolean.FALSE);
        log.setFailureReason(reason);
        persist(log);
    }

    @Override
    public void logCustomerActivated(Long customerId, String customerCode, Long activatedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_ACTIVATED", request);
        assignActor(log, activatedByUserId, null);
        assignEntity(log, "CUSTOMER", customerId, customerCode);
        log.setMetadata(buildMetadata().put("code", customerCode).put("status", "ACTIVE"));
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logCustomerDeactivated(Long customerId, String customerCode, Long deactivatedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_DEACTIVATED", request);
        assignActor(log, deactivatedByUserId, null);
        assignEntity(log, "CUSTOMER", customerId, customerCode);
        log.setMetadata(buildMetadata().put("code", customerCode).put("status", "INACTIVE"));
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logCustomerImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_IMPORTED", request);
        assignActor(log, importedByUserId, null);
        ObjectNode metadata = buildMetadata();
        metadata.put("imported", importedCount);
        metadata.put("errors", errorCount);
        log.setMetadata(metadata);
        log.setSuccess(errorCount == 0);
        if (errorCount > 0) {
            log.setFailureReason("Import completed with errors");
        }
        persist(log);
    }

    @Override
    public void logCustomerExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request) {
        AuditLog log = startLog("CUSTOMER_EXPORTED", request);
        assignActor(log, exportedByUserId, null);
        ObjectNode metadata = buildMetadata();
        metadata.put("count", exportedCount);
        metadata.put("format", format);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logSupplierCreated(
            Long supplierId,
            String supplierCode,
            Long createdByUserId,
            Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_CREATED", request);
        assignActor(log, createdByUserId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierCode);
        ObjectNode metadata = buildMetadata();
        metadata.put("code", supplierCode);
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(null, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logSupplierUpdated(Long supplierId, String supplierCode, Long updatedByUserId,
            java.util.Map<String, String> oldValues, java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_UPDATED", request);
        assignActor(log, updatedByUserId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierCode);
        ObjectNode metadata = buildMetadata();
        metadata.put("code", supplierCode);
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(oldValues, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logSupplierDeleted(Long supplierId, String supplierCode, String reason, Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_DELETED", request);
        assignActor(log, deletedByUserId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierCode);
        ObjectNode metadata = buildMetadata();
        metadata.put("code", supplierCode);
        metadata.put("reason", reason);
        log.setMetadata(metadata);
        log.setReason(trimReason(String.format("id:%s,code:%s", supplierId, supplierCode)));
        log.setSuccess(Boolean.FALSE);
        log.setFailureReason(reason);
        persist(log);
    }

    @Override
    public void logSupplierActivated(Long supplierId, String supplierCode, Long activatedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_ACTIVATED", request);
        assignActor(log, activatedByUserId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierCode);
        log.setMetadata(buildMetadata().put("code", supplierCode).put("status", "ACTIVE"));
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logSupplierDeactivated(Long supplierId, String supplierCode, Long deactivatedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_DEACTIVATED", request);
        assignActor(log, deactivatedByUserId, null);
        assignEntity(log, "SUPPLIER", supplierId, supplierCode);
        log.setMetadata(buildMetadata().put("code", supplierCode).put("status", "INACTIVE"));
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logSupplierImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_IMPORTED", request);
        assignActor(log, importedByUserId, null);
        ObjectNode metadata = buildMetadata();
        metadata.put("imported", importedCount);
        metadata.put("errors", errorCount);
        log.setMetadata(metadata);
        log.setSuccess(errorCount == 0);
        if (errorCount > 0) {
            log.setFailureReason("Import completed with errors");
        }
        persist(log);
    }

    @Override
    public void logSupplierExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request) {
        AuditLog log = startLog("SUPPLIER_EXPORTED", request);
        assignActor(log, exportedByUserId, null);
        ObjectNode metadata = buildMetadata();
        metadata.put("count", exportedCount);
        metadata.put("format", format);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logBankAccountCreated(
            Long bankAccountId,
            String accountNumber,
            Long createdByUserId,
            Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_CREATED", request);
        assignActor(log, createdByUserId, null);
        assignEntity(log, "BANK_ACCOUNT", bankAccountId, accountNumber);
        ObjectNode metadata = buildMetadata();
        metadata.put("accountNumber", accountNumber);
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(null, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logBankAccountUpdated(Long bankAccountId, String accountNumber, Long updatedByUserId,
            java.util.Map<String, String> oldValues, java.util.Map<String, String> newValues, String reason,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_UPDATED", request);
        assignActor(log, updatedByUserId, null);
        assignEntity(log, "BANK_ACCOUNT", bankAccountId, accountNumber);
        ObjectNode metadata = buildMetadata();
        metadata.put("accountNumber", accountNumber);
        if (StringUtils.hasText(reason)) {
            metadata.put("reason", reason);
        }
        log.setMetadata(metadata);
        ObjectNode changes = buildChangePayload(oldValues, newValues);
        if (changes != null) {
            log.setChanges(changes);
        }
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logBankAccountDeleted(Long bankAccountId, String accountNumber, String reason, Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_DELETED", request);
        assignActor(log, deletedByUserId, null);
        assignEntity(log, "BANK_ACCOUNT", bankAccountId, accountNumber);
        ObjectNode metadata = buildMetadata();
        metadata.put("accountNumber", accountNumber);
        metadata.put("reason", reason);
        log.setMetadata(metadata);
        log.setReason(trimReason(String.format("id:%s,acc:%s", bankAccountId, accountNumber)));
        log.setSuccess(Boolean.FALSE);
        log.setFailureReason(reason);
        persist(log);
    }

    @Override
    public void logBankAccountActivated(Long bankAccountId, String accountNumber, Long activatedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_ACTIVATED", request);
        assignActor(log, activatedByUserId, null);
        assignEntity(log, "BANK_ACCOUNT", bankAccountId, accountNumber);
        log.setMetadata(buildMetadata().put("accountNumber", accountNumber).put("status", "ACTIVE"));
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logBankAccountDeactivated(Long bankAccountId, String accountNumber, Long deactivatedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_DEACTIVATED", request);
        assignActor(log, deactivatedByUserId, null);
        assignEntity(log, "BANK_ACCOUNT", bankAccountId, accountNumber);
        log.setMetadata(buildMetadata().put("accountNumber", accountNumber).put("status", "INACTIVE"));
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logBankAccountExport(int exportedCount, String format, Long exportedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_EXPORTED", request);
        assignActor(log, exportedByUserId, null);
        ObjectNode metadata = buildMetadata();
        metadata.put("count", exportedCount);
        metadata.put("format", format);
        log.setMetadata(metadata);
        log.setSuccess(Boolean.TRUE);
        persist(log);
    }

    @Override
    public void logBankAccountImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        AuditLog log = startLog("BANK_ACCOUNT_IMPORTED", request);
        assignActor(log, importedByUserId, null);
        ObjectNode metadata = buildMetadata();
        metadata.put("imported", importedCount);
        metadata.put("errors", errorCount);
        log.setMetadata(metadata);
        log.setSuccess(errorCount == 0);
        if (errorCount > 0) {
            log.setFailureReason("Import completed with errors");
        }
        persist(log);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logImportRow(
            Long companyId,
            Long userId,
            ImportType importType,
            UUID attemptId,
            String sourceFilename,
            int rowNumber,
            String status,
            Object beforePayload,
            Object afterPayload,
            String message,
            String ipAddress,
            String userAgent) {
        if (companyId == null || userId == null || importType == null || attemptId == null) {
            throw new IllegalArgumentException("companyId, userId, importType, and attemptId are required");
        }
        ImportAuditEntry entry = new ImportAuditEntry();
        entry.setId(UUID.randomUUID());
        entry.setCompanyId(companyId);
        entry.setImportType(importType.getPathSegment());
        entry.setAttemptId(attemptId);
        entry.setSourceFilename(sourceFilename);
        entry.setRowNumber(rowNumber);
        entry.setStatus(status);
        entry.setMessage(message);
        entry.setBeforePayload(toJson(beforePayload));
        entry.setAfterPayload(toJson(afterPayload));
        entry.setCreatedBy(userId);
        entry.setCreatedAt(Instant.now());
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(userAgent);
        importAuditEntryRepository.save(entry);
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payload for import audit entry", ex);
        }
    }

    @Override
    public void logAuditExport(
            Long companyId,
            Long userId,
            int recordCount,
            String format,
            String contentHash,
            HttpServletRequest request) {
        AuditLog log = startLog("AUDIT_EXPORT", request);
        log.setCompanyId(companyId != null ? companyId : CompanyContext.getCompanyId());
        assignActor(log, userId, null);
        assignEntity(log, "AUDIT_LOG", null, "Audit Log Export");
        ObjectNode metadata = buildMetadata();
        metadata.put("count", recordCount);
        if (format != null) {
            metadata.put("format", format);
        }
        if (contentHash != null) {
            metadata.put("hash", contentHash);
        }
        log.setMetadata(metadata);
        persist(log);
    }

    @Override
    public void logDataIntegrityScan(
            UUID jobId,
            Long companyId,
            Long userId,
            int findingsCount,
            boolean throttled,
            HttpServletRequest request) {
        AuditLog log = startLog("DATA_INTEGRITY_SCAN", request);
        log.setCompanyId(companyId != null ? companyId : CompanyContext.getCompanyId());
        assignActor(log, userId, null);
        assignEntity(log, "DATA_INTEGRITY_JOB", jobId, jobId != null ? jobId.toString() : null);
        ObjectNode metadata = buildMetadata();
        if (jobId != null) {
            metadata.put("jobId", jobId.toString());
        }
        metadata.put("findings", findingsCount);
        metadata.put("status", throttled ? "THROTTLED" : (findingsCount == 0 ? "CLEAN" : "FINDINGS"));
        log.setMetadata(metadata);
        if (throttled) {
            log.setSuccess(Boolean.FALSE);
            log.setFailureReason("THROTTLED");
        } else if (findingsCount > 0) {
            log.setSuccess(Boolean.FALSE);
            log.setFailureReason("FINDINGS_REPORTED");
        }
        persist(log);
    }

    @Override
    public void logFraudDetection(
            Long userId,
            Long accountId,
            String accountCode,
            int lineNumber,
            java.math.BigDecimal attemptedAmount,
            String fraudType,
            HttpServletRequest request) {
        AuditLog log = startLog("FRAUD_DETECTION", request);
        log.setEventType("SECURITY");
        assignActor(log, userId, null);
        assignEntity(log, "ACCOUNT", accountId, accountCode);
        log.setSuccess(Boolean.FALSE);
        log.setFailureReason("POSSIBLE_FRAUD");

        // Build reason field (truncate to fit VARCHAR(50))
        String reason = String.format("type:%s,line:%d,amt:%s",
                fraudType, lineNumber, attemptedAmount != null ? attemptedAmount.toString() : "N/A");
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);

        // Build metadata with full details
        ObjectNode metadata = buildMetadata();
        metadata.put("fraudType", fraudType);
        metadata.put("lineNumber", lineNumber);
        if (attemptedAmount != null) {
            metadata.put("attemptedAmount", attemptedAmount.toString());
        }
        if (accountId != null) {
            metadata.put("accountId", accountId);
        }
        if (accountCode != null) {
            metadata.put("accountCode", accountCode);
        }
        log.setMetadata(metadata);

        persist(log);
    }

    @Override
    public void logBlockedAttempt(
            Long userId,
            Long accountId,
            String accountCode,
            int lineNumber,
            String fieldName,
            String reason,
            String attemptType,
            HttpServletRequest request) {
        AuditLog log = startLog("VALIDATION_BLOCKED", request);
        log.setEventType("VALIDATION");
        assignActor(log, userId, null);
        assignEntity(log, "ACCOUNT", accountId, accountCode);
        log.setSuccess(Boolean.FALSE);
        log.setFailureReason(attemptType);

        // Build reason field (truncate to fit VARCHAR(50))
        String reasonText = String.format("type:%s,line:%d,field:%s",
                attemptType, lineNumber, fieldName != null ? fieldName : "N/A");
        if (reason != null && !reason.isBlank()) {
            String fullReason = reasonText + ",msg:" + reason;
            if (fullReason.length() > 50) {
                fullReason = fullReason.substring(0, 47) + "...";
            }
            reasonText = fullReason;
        } else if (reasonText.length() > 50) {
            reasonText = reasonText.substring(0, 47) + "...";
        }
        log.setReason(reasonText);

        // Build metadata with full details
        ObjectNode metadata = buildMetadata();
        metadata.put("attemptType", attemptType);
        metadata.put("lineNumber", lineNumber);
        if (fieldName != null) {
            metadata.put("fieldName", fieldName);
        }
        if (reason != null) {
            metadata.put("reason", reason);
        }
        if (accountId != null) {
            metadata.put("accountId", accountId);
        }
        if (accountCode != null) {
            metadata.put("accountCode", accountCode);
        }
        log.setMetadata(metadata);

        persist(log);
    }

    @Override
    public void logVoucherEvent(
            UUID voucherId,
            String voucherNumber,
            String action,
            com.fasterxml.jackson.databind.JsonNode beforeSnapshot,
            com.fasterxml.jackson.databind.JsonNode afterSnapshot,
            String diffHash,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("VOUCHER");
            assignEntity(log, "VOUCHER", voucherId, voucherNumber);

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build changes JSON with before/after snapshots
            ObjectNode changes = objectMapper.createObjectNode();
            if (beforeSnapshot != null) {
                changes.set("before", beforeSnapshot);
            }
            if (afterSnapshot != null) {
                changes.set("after", afterSnapshot);
            }
            if (!changes.isEmpty()) {
                log.setChanges(changes);
            }

            // Build metadata with diff hash and summary
            ObjectNode metadata = buildMetadata();
            if (diffHash != null) {
                metadata.put("diffHash", diffHash);
            }
            metadata.put("voucherId", voucherId.toString());
            metadata.put("voucherNumber", voucherNumber);
            log.setMetadata(metadata);

            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log voucher event for voucher {}: {}", voucherId, e.getMessage(), e);
        }
    }

    @Override
    public void logBatchVoucherAction(
            java.util.List<UUID> voucherIds,
            String action,
            AuditService.BatchActionStats stats,
            java.time.Instant startTime,
            java.time.Instant endTime,
            String summary,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("VOUCHER_BATCH");

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build metadata with batch action details
            ObjectNode metadata = buildMetadata();
            metadata.put("action", action);
            metadata.put("summary", summary != null ? summary : "");

            // Add voucher IDs list
            com.fasterxml.jackson.databind.node.ArrayNode voucherIdsArray = objectMapper.createArrayNode();
            if (voucherIds != null) {
                for (UUID voucherId : voucherIds) {
                    voucherIdsArray.add(voucherId.toString());
                }
            }
            metadata.set("voucherIds", voucherIdsArray);

            // Add statistics
            if (stats != null) {
                ObjectNode statsNode = objectMapper.createObjectNode();
                statsNode.put("successCount", stats.getSuccessCount());
                statsNode.put("failureCount", stats.getFailureCount());
                statsNode.put("totalCount", stats.getTotalCount());
                metadata.set("stats", statsNode);
            }

            // Add timestamps
            if (startTime != null) {
                metadata.put("startTime", startTime.toString());
            }
            if (endTime != null) {
                metadata.put("endTime", endTime.toString());
            }
            if (startTime != null && endTime != null) {
                long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
                metadata.put("durationMs", durationMs);
            }

            log.setMetadata(metadata);
            log.setSuccess(stats != null && stats.getFailureCount() == 0);
            if (stats != null && stats.getFailureCount() > 0) {
                log.setFailureReason(String.format("Batch action completed with %d failures", stats.getFailureCount()));
            }

            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log batch voucher action: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPeriodClosed(UUID periodId, String reason, String hashDigest) {
        try {
            AuditLog log = startLog("PERIOD_CLOSED", null);
            log.setEventType("PERIOD_MANAGEMENT");

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build metadata with period close details
            ObjectNode metadata = buildMetadata();
            metadata.put("periodId", periodId != null ? periodId.toString() : "");
            metadata.put("reason", reason != null ? reason : "");
            metadata.put("hashDigest", hashDigest != null ? hashDigest : "");

            log.setMetadata(metadata);
            log.setSuccess(true);

            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log period close for period {}: {}", periodId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPeriodReopened(UUID periodId, String reason, String approvalMetadata, String hashDigest) {
        try {
            AuditLog log = startLog("PERIOD_REOPENED", null);
            log.setEventType("PERIOD_MANAGEMENT");

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build metadata with period reopen details
            ObjectNode metadata = buildMetadata();
            metadata.put("periodId", periodId != null ? periodId.toString() : "");
            metadata.put("reason", reason != null ? reason : "");
            metadata.put("approvalMetadata", approvalMetadata != null ? approvalMetadata : "");
            metadata.put("hashDigest", hashDigest != null ? hashDigest : "");

            log.setMetadata(metadata);
            log.setSuccess(true);

            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log period reopen for period {}: {}", periodId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPeriodValidationBlocked(UUID periodId, String operation, String reason) {
        try {
            AuditLog log = startLog("PERIOD_VALIDATION_BLOCKED", null);
            log.setEventType("PERIOD_VALIDATION");
            log.setSuccess(false);
            log.setFailureReason(reason);

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build metadata with validation block details
            ObjectNode metadata = buildMetadata();
            metadata.put("periodId", periodId != null ? periodId.toString() : "");
            metadata.put("operation", operation != null ? operation : "");
            metadata.put("reason", reason != null ? reason : "");

            log.setMetadata(metadata);

            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log period validation block for period {}: {}", periodId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttachmentDownload(
            UUID attachmentId,
            UUID voucherId,
            String fileName,
            Long fileSize,
            String mimeType,
            Long downloadedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("ATTACHMENT_DOWNLOAD", request);
            assignActor(log, downloadedByUserId, null);
            assignEntity(log, "VOUCHER_ATTACHMENT", attachmentId != null ? attachmentId.toString() : null, fileName);

            ObjectNode metadata = buildMetadata();
            metadata.put("attachmentId", attachmentId != null ? attachmentId.toString() : "");
            metadata.put("voucherId", voucherId != null ? voucherId.toString() : "");
            metadata.put("fileName", fileName != null ? fileName : "");
            metadata.put("fileSize", fileSize != null ? fileSize : 0);
            metadata.put("mimeType", mimeType != null ? mimeType : "");

            log.setMetadata(metadata);
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log attachment download for attachment {}: {}", attachmentId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttachmentView(
            UUID attachmentId,
            UUID voucherId,
            String fileName,
            Long fileSize,
            String mimeType,
            Long viewedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("ATTACHMENT_VIEW", request);
            assignActor(log, viewedByUserId, null);
            assignEntity(log, "VOUCHER_ATTACHMENT", attachmentId != null ? attachmentId.toString() : null, fileName);

            ObjectNode metadata = buildMetadata();
            metadata.put("attachmentId", attachmentId != null ? attachmentId.toString() : "");
            metadata.put("voucherId", voucherId != null ? voucherId.toString() : "");
            metadata.put("fileName", fileName != null ? fileName : "");
            metadata.put("fileSize", fileSize != null ? fileSize : 0);
            metadata.put("mimeType", mimeType != null ? mimeType : "");

            log.setMetadata(metadata);
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log attachment view for attachment {}: {}", attachmentId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAttachmentDelete(
            UUID attachmentId,
            UUID voucherId,
            String fileName,
            Long fileSize,
            String mimeType,
            String reason,
            Long deletedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("ATTACHMENT_DELETE", request);
            assignActor(log, deletedByUserId, null);
            assignEntity(log, "VOUCHER_ATTACHMENT", attachmentId != null ? attachmentId.toString() : null, fileName);

            ObjectNode metadata = buildMetadata();
            metadata.put("attachmentId", attachmentId != null ? attachmentId.toString() : "");
            metadata.put("voucherId", voucherId != null ? voucherId.toString() : "");
            metadata.put("fileName", fileName != null ? fileName : "");
            metadata.put("fileSize", fileSize != null ? fileSize : 0);
            metadata.put("mimeType", mimeType != null ? mimeType : "");
            metadata.put("reason", reason != null ? reason : "");

            log.setMetadata(metadata);
            log.setReason(trimReason(reason));
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log attachment delete for attachment {}: {}", attachmentId, e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillOperationFailed(
            UUID billId,
            String billNumber,
            String action,
            String reason,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("PURCHASE_BILL");
            log.setSuccess(Boolean.FALSE);
            log.setFailureReason(trimReason(reason));

            if (billId != null) {
                assignEntity(log, "PURCHASE_BILL", billId, billNumber);
            }

            assignActor(log, getCurrentUserId(), null);

            ObjectNode metadata = buildMetadata();
            if (billId != null)
                metadata.put("billId", billId.toString());
            if (billNumber != null)
                metadata.put("billNumber", billNumber);
            log.setMetadata(metadata);

            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log purchase bill operation failure: {}", e.getMessage());
        }
    }

    @Override
    public void logPaymentOperationFailed(
            UUID paymentId,
            String paymentNumber,
            String action,
            String reason,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("PAYMENT");
            log.setSuccess(Boolean.FALSE);
            log.setFailureReason(trimReason(reason));

            if (paymentId != null) {
                assignEntity(log, "PAYMENT", paymentId, paymentNumber);
            }

            assignActor(log, getCurrentUserId(), null);

            ObjectNode metadata = buildMetadata();
            if (paymentId != null)
                metadata.put("paymentId", paymentId.toString());
            if (paymentNumber != null)
                metadata.put("paymentNumber", paymentNumber);
            log.setMetadata(metadata);

            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log payment operation failure: {}", e.getMessage());
        }
    }

    @Override
    public void logDeleteAttemptFailed(
            String entityType,
            String entityId,
            String reason,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(entityType + "_DELETE_FAILED", request);
            log.setEventType(entityType);
            log.setSuccess(Boolean.FALSE);
            log.setFailureReason(trimReason(reason));

            assignEntity(log, entityType, entityId, null);
            assignActor(log, getCurrentUserId(), null);

            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log delete attempt failure: {}", e.getMessage());
        }
    }

    @Override
    public void logPurchaseBillEvent(
            UUID billId,
            String billNumber,
            String action,
            com.fasterxml.jackson.databind.JsonNode beforeSnapshot,
            com.fasterxml.jackson.databind.JsonNode afterSnapshot,
            String diffHash,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("PURCHASE_BILL");
            assignEntity(log, "PURCHASE_BILL", billId, billNumber);

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build changes JSON with before/after snapshots
            ObjectNode changes = objectMapper.createObjectNode();
            if (beforeSnapshot != null) {
                changes.set("before", beforeSnapshot);
            }
            if (afterSnapshot != null) {
                changes.set("after", afterSnapshot);
            }
            if (!changes.isEmpty()) {
                log.setChanges(changes);
            }

            // Build metadata with diff hash and summary
            ObjectNode metadata = buildMetadata();
            if (diffHash != null) {
                metadata.put("diffHash", diffHash);
            }
            metadata.put("billId", billId.toString());
            metadata.put("billNumber", billNumber);
            log.setMetadata(metadata);

            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill event for bill {}: {}", billId, e.getMessage(), e);
        }
    }

    @Override
    public void logPaymentEvent(
            UUID paymentId,
            String paymentNumber,
            String action,
            com.fasterxml.jackson.databind.JsonNode beforeSnapshot,
            com.fasterxml.jackson.databind.JsonNode afterSnapshot,
            String diffHash,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("PAYMENT");
            assignEntity(log, "PAYMENT", paymentId, paymentNumber);

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build changes JSON with before/after snapshots
            ObjectNode changes = objectMapper.createObjectNode();
            if (beforeSnapshot != null) {
                changes.set("before", beforeSnapshot);
            }
            if (afterSnapshot != null) {
                changes.set("after", afterSnapshot);
            }
            if (!changes.isEmpty()) {
                log.setChanges(changes);
            }

            // Build metadata with diff hash and summary
            ObjectNode metadata = buildMetadata();
            if (diffHash != null) {
                metadata.put("diffHash", diffHash);
            }
            metadata.put("paymentId", paymentId.toString());
            metadata.put("paymentNumber", paymentNumber);
            log.setMetadata(metadata);

            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log payment event for payment {}: {}", paymentId, e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillDeleted(
            UUID billId,
            String billNumber,
            String reason,
            Long deletedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("PURCHASE_BILL_DELETED", request);
            log.setEventType("PURCHASE_BILL");
            assignEntity(log, "PURCHASE_BILL", billId, billNumber);
            assignActor(log, deletedByUserId, null);

            // Build metadata
            ObjectNode metadata = buildMetadata();
            metadata.put("billId", billId.toString());
            metadata.put("billNumber", billNumber);
            if (reason != null && !reason.isBlank()) {
                metadata.put("reason", reason);
            }
            log.setMetadata(metadata);

            // Store reason in reason field (truncate to fit VARCHAR(50))
            String reasonText = String.format("bill:%s,number:%s", billId, billNumber);
            if (reason != null && !reason.isBlank()) {
                String fullReason = reasonText + ",reason:" + reason;
                if (fullReason.length() > 50) {
                    reasonText = reasonText + ",reason:" + reason.substring(0, Math.min(reason.length(), 20)) + "...";
                } else {
                    reasonText = fullReason;
                }
            }
            log.setReason(reasonText.length() > 50 ? reasonText.substring(0, 47) + "..." : reasonText);

            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill deletion for bill {}: {}", billId, e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("PURCHASE_BILL_IMPORT", request);
            log.setEventType("PURCHASE_BILL_IMPORT");
            assignActor(log, importedByUserId, null);

            // Build metadata with import statistics
            ObjectNode metadata = buildMetadata();
            metadata.put("importedCount", importedCount);
            metadata.put("errorCount", errorCount);
            metadata.put("totalCount", importedCount + errorCount);
            log.setMetadata(metadata);

            // Store summary in reason field
            String reasonText = String.format("imported:%d,errors:%d", importedCount, errorCount);
            log.setReason(reasonText.length() > 50 ? reasonText.substring(0, 47) + "..." : reasonText);

            log.setSuccess(errorCount == 0);
            persist(log);
        } catch (Exception e) {
            // Non-blocking: log error but don't break main flow
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill import: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillSubmittedForApproval(
            Long companyId,
            Long submittedByUserId,
            UUID billId,
            java.math.BigDecimal billAmount,
            java.math.BigDecimal thresholdAmount) {
        try {
            AuditLog log = startLog("PURCHASE_BILL_SUBMITTED_FOR_APPROVAL", null);
            log.setCompanyId(companyId);
            assignActor(log, submittedByUserId, null);

            ObjectNode metadata = buildMetadata();
            metadata.put("billId", billId.toString());
            metadata.put("billAmount", billAmount.toString());
            metadata.put("thresholdAmount", thresholdAmount.toString());
            log.setMetadata(metadata);

            log.setReason("Bill submitted for approval");
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill submitted for approval: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillApproved(
            Long companyId, Long approvedByUserId, UUID billId, String approvalReason) {
        try {
            AuditLog log = startLog("PURCHASE_BILL_APPROVED", null);
            log.setCompanyId(companyId);
            assignActor(log, approvedByUserId, null);

            ObjectNode metadata = buildMetadata();
            metadata.put("billId", billId.toString());
            if (approvalReason != null && !approvalReason.isEmpty()) {
                metadata.put("approvalReason", approvalReason);
            }
            log.setMetadata(metadata);

            log.setReason(approvalReason != null ? approvalReason : "Bill approved");
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill approved: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillRejected(
            Long companyId, Long rejectedByUserId, UUID billId, String rejectionReason) {
        try {
            AuditLog log = startLog("PURCHASE_BILL_REJECTED", null);
            log.setCompanyId(companyId);
            assignActor(log, rejectedByUserId, null);

            ObjectNode metadata = buildMetadata();
            metadata.put("billId", billId.toString());
            metadata.put("rejectionReason", rejectionReason);
            log.setMetadata(metadata);

            log.setReason(rejectionReason);
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill rejected: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logPurchaseBillAutoApproved(
            Long companyId,
            Long userId,
            UUID billId,
            java.math.BigDecimal billAmount,
            java.math.BigDecimal thresholdAmount) {
        try {
            AuditLog log = startLog("PURCHASE_BILL_AUTO_APPROVED", null);
            log.setCompanyId(companyId);
            assignActor(log, userId, null);

            ObjectNode metadata = buildMetadata();
            metadata.put("billId", billId.toString());
            metadata.put("billAmount", billAmount.toString());
            metadata.put("thresholdAmount", thresholdAmount.toString());
            log.setMetadata(metadata);

            log.setReason("Auto-approved: amount below threshold");
            log.setSuccess(true);
            persist(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditServiceImpl.class)
                    .error("Failed to log purchase bill auto-approved: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logAgingReminderSent(
            Long supplierId, java.util.List<java.util.UUID> billIds, java.util.List<String> recipients,
            Long companyId) {
        try {
            AuditLog log = startLog("AGING_REMINDER_SENT", null);
            assignActor(log, getCurrentUserId(), null);
            if (supplierId != null) {
                assignEntity(log, "SUPPLIER", supplierId, null);
            }
            ObjectNode metadata = buildMetadata();
            if (billIds != null && !billIds.isEmpty()) {
                metadata.put("billCount", billIds.size());
                metadata.set("billIds", objectMapper.valueToTree(billIds));
            }
            if (recipients != null) {
                metadata.put("recipientCount", recipients.size());
                metadata.set("recipients", objectMapper.valueToTree(recipients));
            }
            log.setMetadata(metadata);
            log.setCompanyId(companyId != null ? companyId : CompanyContext.getCompanyId());
            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log aging reminder sent: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logAgingBatchReminderSent(
            java.util.List<Long> supplierIds, java.util.List<String> recipients, Long companyId) {
        try {
            AuditLog log = startLog("AGING_BATCH_REMINDER_SENT", null);
            assignActor(log, getCurrentUserId(), null);
            ObjectNode metadata = buildMetadata();
            if (supplierIds != null) {
                metadata.put("supplierCount", supplierIds.size());
                metadata.set("supplierIds", objectMapper.valueToTree(supplierIds));
            }
            if (recipients != null) {
                metadata.put("recipientCount", recipients.size());
                metadata.set("recipients", objectMapper.valueToTree(recipients));
            }
            log.setMetadata(metadata);
            log.setCompanyId(companyId != null ? companyId : CompanyContext.getCompanyId());
            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log aging batch reminder sent: {}", e.getMessage(), e);
        }
    }

    @Override
    public int purgeAuditLogs(Long companyId, Long userId, Instant beforeDate, Long adminUserId) {
        // Only allow if admin
        // (Permission check should be done by caller or via annotations, but service
        // logic here)

        // NOTE: In a real production system, we would archive before deleting.
        // For this implementation, we assume backup has been run separately.

        // Find logs to purge
        // Since JPA Spec deletion is tricky, we'll use direct repository delete or list
        // then delete
        // Direct delete by spec isn't standard in JpaSpecificationExecutor

        // We'll use a custom delete logic
        // WARNING: Deleting large number of rows might be slow. Batching recommended.
        // For MVP, simplistic delete.

        // Since we can't use Specification for delete easily without custom repo
        // method,
        // let's find IDs then delete.

        // Construct Specification for filtering
        org.springframework.data.jpa.domain.Specification<AuditLog> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (beforeDate != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), beforeDate));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        java.util.List<AuditLog> logsToDelete = auditLogRepository.findAll(spec);
        int count = logsToDelete.size();

        if (count > 0) {
            auditLogRepository.deleteAll(logsToDelete);

            // Log the purge event (this must NOT be purged!)
            AuditLog purgeLog = startLog("AUDIT_LOG_PURGE", null);
            purgeLog.setCompanyId(companyId);
            assignActor(purgeLog, adminUserId, null);
            // Manually set actor since we are inside service
            purgeLog.setUserId(adminUserId);
            userRepository.findById(adminUserId).ifPresent(u -> {
                purgeLog.setEmail(u.getEmail());
                purgeLog.setActorRole(u.getRole());
            });

            ObjectNode metadata = buildMetadata();
            metadata.put("purgedCount", count);
            if (userId != null)
                metadata.put("targetUserId", userId);
            if (beforeDate != null)
                metadata.put("beforeDate", beforeDate.toString());
            purgeLog.setMetadata(metadata);
            purgeLog.setReason("GDPR Purge: " + count + " records");
            purgeLog.setSuccess(true);

            // Persist explicitly to bypass chain hash if we deleted previous link?
            // Actually, purging breaks the chain!
            // This is expected in GDPR purge. The new log starts a new chain or continues
            // from whatever is left.
            persist(purgeLog);
        }

        return count;
    }

    /**
     * Get current user ID from SecurityContext.
     * 
     * @return user ID or null if not available
     */
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            // Non-blocking: return null if user ID cannot be determined
            return null;
        }
    }

    @Override
    public void logSalesInvoiceEvent(
            UUID invoiceId,
            String invoiceNumber,
            String action,
            JsonNode beforeSnapshot,
            JsonNode afterSnapshot,
            String diffHash,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("SALES_INVOICE");
            assignEntity(log, "SALES_INVOICE", invoiceId, invoiceNumber);

            // Get current user from SecurityContext
            Long userId = getCurrentUserId();
            if (userId != null) {
                assignActor(log, userId, null);
            }

            // Build changes JSON with before/after snapshots
            ObjectNode changes = objectMapper.createObjectNode();
            if (beforeSnapshot != null) {
                changes.set("before", beforeSnapshot);
            }
            if (afterSnapshot != null) {
                changes.set("after", afterSnapshot);
            }
            if (!changes.isEmpty()) {
                log.setChanges(changes);
            }

            // Build metadata with diff hash and summary
            ObjectNode metadata = buildMetadata();
            if (diffHash != null) {
                metadata.put("diffHash", diffHash);
            }
            metadata.put("invoiceId", invoiceId.toString());
            metadata.put("invoiceNumber", invoiceNumber);
            log.setMetadata(metadata);

            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log sales invoice event for invoice {}: {}", invoiceId, e.getMessage(), e);
        }
    }

    @Override
    public void logSalesInvoiceDeleted(
            UUID invoiceId,
            String invoiceNumber,
            String reason,
            Long deletedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("SALES_INVOICE_DELETED", request);
            log.setEventType("SALES_INVOICE");
            assignEntity(log, "SALES_INVOICE", invoiceId, invoiceNumber);

            // Assign actor
            if (deletedByUserId != null) {
                assignActor(log, deletedByUserId, null);
            }

            ObjectNode metadata = buildMetadata();
            metadata.put("invoiceId", invoiceId.toString());
            metadata.put("invoiceNumber", invoiceNumber);
            log.setMetadata(metadata);

            log.setReason(reason);
            log.setSuccess(Boolean.TRUE);

            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log sales invoice deletion for invoice {}: {}", invoiceId, e.getMessage(), e);
        }
    }

    @Override
    public void logSalesInvoiceImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog("SALES_INVOICE_IMPORTED", request);
            log.setEventType("SALES_INVOICE_BATCH");

            if (importedByUserId != null) {
                assignActor(log, importedByUserId, null);
            }

            ObjectNode metadata = buildMetadata();
            metadata.put("importedCount", importedCount);
            metadata.put("errorCount", errorCount);
            log.setMetadata(metadata);

            log.setSuccess(Boolean.TRUE);
            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log sales invoice import: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logSalesInvoiceOperationFailed(
            UUID invoiceId,
            String invoiceNumber,
            String action,
            String reason,
            HttpServletRequest request) {
        try {
            AuditLog log = startLog(action, request);
            log.setEventType("SALES_INVOICE");
            if (invoiceId != null) {
                assignEntity(log, "SALES_INVOICE", invoiceId, invoiceNumber);
            }

            ObjectNode metadata = buildMetadata();
            if (invoiceNumber != null) {
                metadata.put("invoiceNumber", invoiceNumber);
            }
            log.setMetadata(metadata);

            log.setFailureReason(reason);
            log.setSuccess(Boolean.FALSE);

            persist(log);
        } catch (Exception e) {
            logger.error("Failed to log sales invoice operation failure: {}", e.getMessage(), e);
        }
    }
}
