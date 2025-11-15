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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AuditServiceImpl implements AuditService {

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

  private void persist(AuditLog log) {
        auditLogRepository.save(log);
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
        // Store original and reversal voucher IDs/numbers, and reason (truncate to fit VARCHAR(50))
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
}
