package com.accounting.service.impl;

import com.accounting.entity.AuditLog;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.service.AuditService;
import com.accounting.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void logLoginSuccess(User user, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("LOGIN_SUCCESS");
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logLoginFailure(User user, String email, String reason, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        if (user != null) {
            log.setUserId(user.getId());
            log.setEmail(user.getEmail());
        } else {
            log.setEmail(email);
        }
        log.setAction("LOGIN_FAILURE");
        log.setReason(reason);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logPasswordResetRequest(User user, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("PASSWORD_RESET_REQUESTED");
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logPasswordResetCompleted(User user, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(user.getId());
        log.setEmail(user.getEmail());
        log.setAction("PASSWORD_RESET_COMPLETED");
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
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
        userRepository
                .findById(deletedByUserId)
                .ifPresent(user -> log.setEmail(user.getEmail()));
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
    public void logCompanySettingsUpdated(
            Long companyId,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(updatedByUserId);
        if (updatedByUserId != null) {
            userRepository.findById(updatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("COMPANY_SETTINGS_UPDATED");
        // Compact change summary for VARCHAR(50): c:{id},f1, f2, f3
        StringBuilder sb = new StringBuilder();
        sb.append("c:").append(companyId).append(",");
        int count = 0;
        for (String field : newValues.keySet()) {
            if (count > 0)
                sb.append(" ");
            sb.append(field);
            count++;
            if (sb.length() > 45)
                break;
        }
        String reason = sb.toString();
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(java.time.Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logReportExport(Long companyId, Long userId, String format, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("REPORT_EXPORTED");
        String reason = String.format("c:%s,f:%s", companyId, format);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }
}
