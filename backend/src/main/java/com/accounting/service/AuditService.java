package com.accounting.service;

import com.accounting.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuditService {
    void logLoginSuccess(User user, HttpServletRequest request);

    void logLoginFailure(User user, String email, String reason, HttpServletRequest request);

    void logPasswordResetRequest(User user, HttpServletRequest request);

    void logPasswordResetCompleted(User user, HttpServletRequest request);

    /**
     * Log role change operation. Records old_role, new_role, changed_by_user_id,
     * target_user_id,
     * timestamp, and IP address.
     *
     * @param targetUser      user whose role is being changed
     * @param oldRole         previous role value
     * @param newRole         new role value
     * @param changedByUserId ID of user who made the change
     * @param request         HTTP request for IP address and user agent
     */
    void logRoleChange(
            User targetUser, String oldRole, String newRole, Long changedByUserId, HttpServletRequest request);

    /**
     * Log invitation creation. Records inviter, invitee email, company, status,
     * timestamp, and IP
     * address.
     *
     * @param inviterUserId ID of user who created the invitation
     * @param inviteeEmail  email address of invited user
     * @param companyId     company ID for the invitation
     * @param role          role assigned to the invitation
     * @param request       HTTP request for IP address and user agent
     */
    void logInvitationCreated(
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            String role,
            HttpServletRequest request);

    /**
     * Log invitation acceptance. Records inviter, invitee email, company, status,
     * timestamp, and IP
     * address.
     *
     * @param invitationId  ID of the invitation
     * @param inviterUserId ID of user who created the invitation
     * @param inviteeEmail  email address of invited user
     * @param companyId     company ID for the invitation
     * @param request       HTTP request for IP address and user agent
     */
    void logInvitationAccepted(
            Long invitationId,
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            HttpServletRequest request);

    /**
     * Log invitation expiration. Records inviter, invitee email, company, status,
     * timestamp, and IP
     * address.
     *
     * @param invitationId  ID of the invitation
     * @param inviterUserId ID of user who created the invitation
     * @param inviteeEmail  email address of invited user
     * @param companyId     company ID for the invitation
     * @param request       HTTP request for IP address and user agent
     */
    void logInvitationExpired(
            Long invitationId,
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            HttpServletRequest request);

    /**
     * Log invitation cancellation. Records inviter, invitee email, company, status,
     * timestamp, and IP
     * address.
     *
     * @param invitationId  ID of the invitation
     * @param inviterUserId ID of user who created/cancelled the invitation
     * @param inviteeEmail  email address of invited user
     * @param companyId     company ID for the invitation
     * @param request       HTTP request for IP address and user agent
     */
    void logInvitationCancelled(
            Long invitationId,
            Long inviterUserId,
            String inviteeEmail,
            Long companyId,
            HttpServletRequest request);

    /**
     * Log user creation. Records user details, creator, timestamp, and IP address.
     *
     * @param user            created user
     * @param createdByUserId ID of user who created this user
     * @param request         HTTP request for IP address and user agent
     */
    void logUserCreated(User user, Long createdByUserId, HttpServletRequest request);

    /**
     * Log user update. Records user details, updater, old/new values, timestamp,
     * and IP address.
     *
     * @param user            updated user
     * @param updatedByUserId ID of user who made the update
     * @param oldValues       map of old field values (key: field name, value: old
     *                        value)
     * @param newValues       map of new field values (key: field name, value: new
     *                        value)
     * @param request         HTTP request for IP address and user agent
     */
    void logUserUpdated(
            User user,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request);

    /**
     * Log user deactivation. Records user details, deactivator, timestamp, and IP
     * address.
     *
     * @param user                deactivated user
     * @param deactivatedByUserId ID of user who deactivated this user
     * @param request             HTTP request for IP address and user agent
     */
    void logUserDeactivated(User user, Long deactivatedByUserId, HttpServletRequest request);

    /**
     * Log user activation. Records user details, activator, timestamp, and IP
     * address.
     *
     * @param user              activated user
     * @param activatedByUserId ID of user who activated this user
     * @param request           HTTP request for IP address and user agent
     */
    void logUserActivated(User user, Long activatedByUserId, HttpServletRequest request);

    /**
     * Log password reset initiated by admin. Records admin user, target user,
     * timestamp, and IP address.
     *
     * @param targetUser  user whose password is being reset
     * @param adminUserId ID of admin who initiated the reset
     * @param request     HTTP request for IP address and user agent
     */
    void logPasswordResetByAdmin(User targetUser, Long adminUserId, HttpServletRequest request);

    /**
     * Log profile update. Records user details, updated fields, timestamp, and IP
     * address.
     *
     * @param user          user whose profile was updated
     * @param updatedFields map of updated fields (key: field name, value: new
     *                      value)
     * @param request       HTTP request for IP address and user agent
     */
    void logProfileUpdated(User user, java.util.Map<String, String> updatedFields, HttpServletRequest request);

    /**
     * Log password change. Records user details, timestamp, and IP address.
     *
     * @param user    user who changed their password
     * @param request HTTP request for IP address and user agent
     */
    void logPasswordChanged(User user, HttpServletRequest request);

    /**
     * Log voucher deletion. Records voucher ID, deletion reason, user who deleted,
     * timestamp, and IP address.
     *
     * @param voucherId       voucher ID that was deleted
     * @param voucherNumber   voucher number for reference
     * @param reason          deletion reason (required)
     * @param deletedByUserId ID of user who deleted the voucher
     * @param request         HTTP request for IP address and user agent
     */
    void logVoucherDeleted(
            java.util.UUID voucherId,
            String voucherNumber,
            String reason,
            Long deletedByUserId,
            HttpServletRequest request);

    /**
     * Log company settings update with old/new values.
     *
     * @param companyId       target company id
     * @param updatedByUserId user who performed the update
     * @param oldValues       map of old values
     * @param newValues       map of new values
     * @param request         HTTP request for IP address and user agent
     */
    void logCompanySettingsUpdated(
            Long companyId,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request);

    /**
     * Log report export event (PDF/XLSX). Records user, company, format, timestamp,
     * and IP/UA.
     *
     * @param companyId company id from context
     * @param userId    user who triggered export
     * @param format    export format (e.g., PDF, XLSX)
     * @param request   HTTP request for IP address and user agent
     */
    void logReportExport(Long companyId, Long userId, String format, HttpServletRequest request);

    /**
     * Log customer creation. Records customer details, creator, timestamp, and IP
     * address.
     *
     * @param customerId      customer ID
     * @param customerCode    customer code
     * @param createdByUserId ID of user who created this customer
     * @param request         HTTP request for IP address and user agent
     */
    void logCustomerCreated(Long customerId, String customerCode, Long createdByUserId, HttpServletRequest request);

    /**
     * Log customer update. Records customer details, updater, old/new values,
     * timestamp, and IP address.
     *
     * @param customerId      customer ID
     * @param customerCode    customer code
     * @param updatedByUserId ID of user who made the update
     * @param oldValues       map of old field values (key: field name, value: old
     *                        value)
     * @param newValues       map of new field values (key: field name, value: new
     *                        value)
     * @param request         HTTP request for IP address and user agent
     */
    void logCustomerUpdated(
            Long customerId,
            String customerCode,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request);

    /**
     * Log customer deletion. Records customer details, deletion reason, user who
     * deleted, timestamp, and IP address.
     *
     * @param customerId      customer ID
     * @param customerCode    customer code
     * @param reason          deletion reason (required)
     * @param deletedByUserId ID of user who deleted the customer
     * @param request         HTTP request for IP address and user agent
     */
    void logCustomerDeleted(
            Long customerId, String customerCode, String reason, Long deletedByUserId, HttpServletRequest request);

    /**
     * Log customer activation. Records customer details, activator, timestamp, and
     * IP address.
     *
     * @param customerId        customer ID
     * @param customerCode      customer code
     * @param activatedByUserId ID of user who activated this customer
     * @param request           HTTP request for IP address and user agent
     */
    void logCustomerActivated(Long customerId, String customerCode, Long activatedByUserId, HttpServletRequest request);

    /**
     * Log customer deactivation. Records customer details, deactivator, timestamp,
     * and IP address.
     *
     * @param customerId          customer ID
     * @param customerCode        customer code
     * @param deactivatedByUserId ID of user who deactivated this customer
     * @param request             HTTP request for IP address and user agent
     */
    void logCustomerDeactivated(
            Long customerId, String customerCode, Long deactivatedByUserId, HttpServletRequest request);

    /**
     * Log customer import. Records import details, user, timestamp, and IP address.
     *
     * @param importedCount    number of customers imported
     * @param errorCount       number of errors
     * @param importedByUserId ID of user who performed the import
     * @param request          HTTP request for IP address and user agent
     */
    void logCustomerImport(int importedCount, int errorCount, Long importedByUserId, HttpServletRequest request);

    /**
     * Log customer export. Records export details, user, format, timestamp, and IP
     * address.
     *
     * @param exportedCount    number of customers exported
     * @param format           export format (e.g., XLSX, CSV)
     * @param exportedByUserId ID of user who performed the export
     * @param request          HTTP request for IP address and user agent
     */
    void logCustomerExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request);

    /**
     * Log supplier creation. Records supplier details, creator, timestamp, and IP
     * address.
     *
     * @param supplierId      supplier ID
     * @param supplierCode    supplier code
     * @param createdByUserId ID of user who created this supplier
     * @param request         HTTP request for IP address and user agent
     */
    void logSupplierCreated(Long supplierId, String supplierCode, Long createdByUserId, HttpServletRequest request);

    /**
     * Log supplier update. Records supplier details, updater, old/new values,
     * timestamp, and IP address.
     *
     * @param supplierId      supplier ID
     * @param supplierCode    supplier code
     * @param updatedByUserId ID of user who made the update
     * @param oldValues       map of old field values (key: field name, value: old
     *                        value)
     * @param newValues       map of new field values (key: field name, value: new
     *                        value)
     * @param request         HTTP request for IP address and user agent
     */
    void logSupplierUpdated(
            Long supplierId,
            String supplierCode,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            HttpServletRequest request);

    /**
     * Log supplier deletion. Records supplier details, deletion reason, user who
     * deleted, timestamp, and IP address.
     *
     * @param supplierId      supplier ID
     * @param supplierCode    supplier code
     * @param reason          deletion reason (required)
     * @param deletedByUserId ID of user who deleted the supplier
     * @param request         HTTP request for IP address and user agent
     */
    void logSupplierDeleted(
            Long supplierId, String supplierCode, String reason, Long deletedByUserId, HttpServletRequest request);

    /**
     * Log supplier activation. Records supplier details, activator, timestamp, and
     * IP address.
     *
     * @param supplierId        supplier ID
     * @param supplierCode      supplier code
     * @param activatedByUserId ID of user who activated this supplier
     * @param request           HTTP request for IP address and user agent
     */
    void logSupplierActivated(Long supplierId, String supplierCode, Long activatedByUserId, HttpServletRequest request);

    /**
     * Log supplier deactivation. Records supplier details, deactivator, timestamp,
     * and IP address.
     *
     * @param supplierId          supplier ID
     * @param supplierCode        supplier code
     * @param deactivatedByUserId ID of user who deactivated this supplier
     * @param request             HTTP request for IP address and user agent
     */
    void logSupplierDeactivated(
            Long supplierId, String supplierCode, Long deactivatedByUserId, HttpServletRequest request);

    /**
     * Log supplier import. Records import details, user, timestamp, and IP address.
     *
     * @param importedCount    number of suppliers imported
     * @param errorCount       number of errors
     * @param importedByUserId ID of user who performed the import
     * @param request          HTTP request for IP address and user agent
     */
    void logSupplierImport(int importedCount, int errorCount, Long importedByUserId, HttpServletRequest request);

    /**
     * Log supplier export. Records export details, user, format, timestamp, and IP
     * address.
     *
     * @param exportedCount    number of suppliers exported
     * @param format           export format (e.g., XLSX, CSV)
     * @param exportedByUserId ID of user who performed the export
     * @param request          HTTP request for IP address and user agent
     */
    void logSupplierExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request);

    /**
     * Log bank account creation. Records bank account details, creator, timestamp,
     * and IP address.
     *
     * @param bankAccountId   bank account ID
     * @param accountNumber   account number
     * @param createdByUserId ID of user who created this bank account
     * @param request         HTTP request for IP address and user agent
     */
    void logBankAccountCreated(Long bankAccountId, String accountNumber, Long createdByUserId,
            HttpServletRequest request);

    /**
     * Log bank account update. Records bank account details, updater, old/new
     * values, timestamp, and IP address.
     *
     * @param bankAccountId   bank account ID
     * @param accountNumber   account number
     * @param updatedByUserId ID of user who made the update
     * @param oldValues       map of old field values (key: field name, value: old
     *                        value)
     * @param newValues       map of new field values (key: field name, value: new
     *                        value)
     * @param reason          optional reason for change
     * @param request         HTTP request for IP address and user agent
     */
    void logBankAccountUpdated(
            Long bankAccountId,
            String accountNumber,
            Long updatedByUserId,
            java.util.Map<String, String> oldValues,
            java.util.Map<String, String> newValues,
            String reason,
            HttpServletRequest request);

    /**
     * Log bank account deletion. Records bank account details, deletion reason,
     * user who deleted, timestamp, and IP address.
     *
     * @param bankAccountId   bank account ID
     * @param accountNumber   account number
     * @param reason          deletion reason (required)
     * @param deletedByUserId ID of user who deleted the bank account
     * @param request         HTTP request for IP address and user agent
     */
    void logBankAccountDeleted(
            Long bankAccountId, String accountNumber, String reason, Long deletedByUserId, HttpServletRequest request);

    /**
     * Log bank account activation. Records bank account details, activator,
     * timestamp, and IP address.
     *
     * @param bankAccountId     bank account ID
     * @param accountNumber     account number
     * @param activatedByUserId ID of user who activated this bank account
     * @param request           HTTP request for IP address and user agent
     */
    void logBankAccountActivated(Long bankAccountId, String accountNumber, Long activatedByUserId,
            HttpServletRequest request);

    /**
     * Log bank account deactivation. Records bank account details, deactivator,
     * timestamp, and IP address.
     *
     * @param bankAccountId       bank account ID
     * @param accountNumber       account number
     * @param deactivatedByUserId ID of user who deactivated this bank account
     * @param request             HTTP request for IP address and user agent
     */
    void logBankAccountDeactivated(
            Long bankAccountId, String accountNumber, Long deactivatedByUserId, HttpServletRequest request);

    /**
     * Log bank account export. Records export details, user, format, timestamp, and
     * IP address.
     *
     * @param exportedCount    number of bank accounts exported
     * @param format           export format (e.g., XLSX, CSV)
     * @param exportedByUserId ID of user who performed the export
     * @param request          HTTP request for IP address and user agent
     */
    void logBankAccountExport(int exportedCount, String format, Long exportedByUserId, HttpServletRequest request);
}
