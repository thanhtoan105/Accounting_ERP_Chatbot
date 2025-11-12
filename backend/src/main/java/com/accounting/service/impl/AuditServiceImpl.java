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
        log.setAction("REPORT_EXPORT");
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

    @Override
    public void logCustomerCreated(Long customerId, String customerCode, Long createdByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(createdByUserId);
        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_CREATED");
        String reason = String.format("id:%s,code:%s", customerId, customerCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCustomerUpdated(Long customerId, String customerCode, Long updatedByUserId,
            java.util.Map<String, String> oldValues, java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(updatedByUserId);
        if (updatedByUserId != null) {
            userRepository.findById(updatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_UPDATED");
        String reason = String.format("id:%s,code:%s", customerId, customerCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCustomerDeleted(Long customerId, String customerCode, String reason, Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deletedByUserId);
        if (deletedByUserId != null) {
            userRepository.findById(deletedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_DELETED");
        String logReason = String.format("id:%s,code:%s,r:%s", customerId, customerCode, reason);
        if (logReason.length() > 50) {
            logReason = logReason.substring(0, 47) + "...";
        }
        log.setReason(logReason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCustomerActivated(Long customerId, String customerCode, Long activatedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(activatedByUserId);
        if (activatedByUserId != null) {
            userRepository.findById(activatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_ACTIVATED");
        String reason = String.format("id:%s,code:%s", customerId, customerCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCustomerDeactivated(Long customerId, String customerCode, Long deactivatedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deactivatedByUserId);
        if (deactivatedByUserId != null) {
            userRepository.findById(deactivatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_DEACTIVATED");
        String reason = String.format("id:%s,code:%s", customerId, customerCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCustomerImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(importedByUserId);
        if (importedByUserId != null) {
            userRepository.findById(importedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_IMPORTED");
        String reason = String.format("imported:%d,errors:%d", importedCount, errorCount);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logCustomerExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(exportedByUserId);
        if (exportedByUserId != null) {
            userRepository.findById(exportedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("CUSTOMER_EXPORTED");
        String reason = String.format("count:%d,format:%s", exportedCount, format);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logSupplierCreated(Long supplierId, String supplierCode, Long createdByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(createdByUserId);
        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_CREATED");
        String reason = String.format("id:%s,code:%s", supplierId, supplierCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logSupplierUpdated(Long supplierId, String supplierCode, Long updatedByUserId,
            java.util.Map<String, String> oldValues, java.util.Map<String, String> newValues,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(updatedByUserId);
        if (updatedByUserId != null) {
            userRepository.findById(updatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_UPDATED");
        String reason = String.format("id:%s,code:%s", supplierId, supplierCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logSupplierDeleted(Long supplierId, String supplierCode, String reason, Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deletedByUserId);
        if (deletedByUserId != null) {
            userRepository.findById(deletedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_DELETED");
        String logReason = String.format("id:%s,code:%s,r:%s", supplierId, supplierCode, reason);
        if (logReason.length() > 50) {
            logReason = logReason.substring(0, 47) + "...";
        }
        log.setReason(logReason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logSupplierActivated(Long supplierId, String supplierCode, Long activatedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(activatedByUserId);
        if (activatedByUserId != null) {
            userRepository.findById(activatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_ACTIVATED");
        String reason = String.format("id:%s,code:%s", supplierId, supplierCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logSupplierDeactivated(Long supplierId, String supplierCode, Long deactivatedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deactivatedByUserId);
        if (deactivatedByUserId != null) {
            userRepository.findById(deactivatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_DEACTIVATED");
        String reason = String.format("id:%s,code:%s", supplierId, supplierCode);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logSupplierImport(int importedCount, int errorCount, Long importedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(importedByUserId);
        if (importedByUserId != null) {
            userRepository.findById(importedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_IMPORTED");
        String reason = String.format("imported:%d,errors:%d", importedCount, errorCount);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logSupplierExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(exportedByUserId);
        if (exportedByUserId != null) {
            userRepository.findById(exportedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("SUPPLIER_EXPORTED");
        String reason = String.format("count:%d,format:%s", exportedCount, format);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logBankAccountCreated(Long bankAccountId, String accountNumber, Long createdByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(createdByUserId);
        if (createdByUserId != null) {
            userRepository.findById(createdByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("BANK_ACCOUNT_CREATED");
        String reason = String.format("id:%s,acc:%s", bankAccountId, accountNumber);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logBankAccountUpdated(Long bankAccountId, String accountNumber, Long updatedByUserId,
            java.util.Map<String, String> oldValues, java.util.Map<String, String> newValues, String reason,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(updatedByUserId);
        if (updatedByUserId != null) {
            userRepository.findById(updatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("BANK_ACCOUNT_UPDATED");
        String logReason = String.format("id:%s,acc:%s", bankAccountId, accountNumber);
        if (reason != null && !reason.isBlank()) {
            logReason += ",r:" + reason;
        }
        if (logReason.length() > 50) {
            logReason = logReason.substring(0, 47) + "...";
        }
        log.setReason(logReason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logBankAccountDeleted(Long bankAccountId, String accountNumber, String reason, Long deletedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deletedByUserId);
        if (deletedByUserId != null) {
            userRepository.findById(deletedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("BANK_ACCOUNT_DELETED");
        String logReason = String.format("id:%s,acc:%s,r:%s", bankAccountId, accountNumber, reason);
        if (logReason.length() > 50) {
            logReason = logReason.substring(0, 47) + "...";
        }
        log.setReason(logReason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logBankAccountActivated(Long bankAccountId, String accountNumber, Long activatedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(activatedByUserId);
        if (activatedByUserId != null) {
            userRepository.findById(activatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("BANK_ACCOUNT_ACTIVATED");
        String reason = String.format("id:%s,acc:%s", bankAccountId, accountNumber);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logBankAccountDeactivated(Long bankAccountId, String accountNumber, Long deactivatedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(deactivatedByUserId);
        if (deactivatedByUserId != null) {
            userRepository.findById(deactivatedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("BANK_ACCOUNT_DEACTIVATED");
        String reason = String.format("id:%s,acc:%s", bankAccountId, accountNumber);
        if (reason.length() > 50) {
            reason = reason.substring(0, 47) + "...";
        }
        log.setReason(reason);
        log.setIpAddress(request != null ? request.getRemoteAddr() : null);
        log.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }

    @Override
    public void logBankAccountExport(int exportedCount, String format, Long exportedByUserId,
            HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(exportedByUserId);
        if (exportedByUserId != null) {
            userRepository.findById(exportedByUserId).ifPresent(user -> log.setEmail(user.getEmail()));
        }
        log.setAction("BANK_ACCOUNT_EXPORTED");
        String reason = String.format("count:%d,format:%s", exportedCount, format);
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
