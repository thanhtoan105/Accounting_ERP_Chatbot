package com.accounting.service;

import com.accounting.entity.User;
import com.accounting.imports.ImportType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

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
                        User targetUser, String oldRole, String newRole, Long changedByUserId,
                        HttpServletRequest request);

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
         * Log voucher posting. Records voucher ID, voucher number, user who posted,
         * timestamp, and IP address.
         *
         * @param voucherId       voucher ID that was posted
         * @param voucherNumber   voucher number for reference
         * @param postedByUserId ID of user who posted the voucher
         * @param request         HTTP request for IP address and user agent
         */
        void logVoucherPosted(
                        java.util.UUID voucherId,
                        String voucherNumber,
                        Long postedByUserId,
                        HttpServletRequest request);

        /**
         * Log voucher unposting. Records voucher ID, voucher number, reason, user who unposted,
         * timestamp, and IP address.
         *
         * @param voucherId         voucher ID that was unposted
         * @param voucherNumber     voucher number for reference
         * @param reason            unposting reason (required)
         * @param unpostedByUserId  ID of user who unposted the voucher
         * @param request           HTTP request for IP address and user agent
         */
        void logVoucherUnposted(
                        java.util.UUID voucherId,
                        String voucherNumber,
                        String reason,
                        Long unpostedByUserId,
                        HttpServletRequest request);

        /**
         * Log voucher reversal. Records original voucher ID, reversal voucher ID,
         * voucher numbers, reason, user who reversed, timestamp, and IP address.
         *
         * @param originalVoucherId  original voucher ID that was reversed
         * @param originalVoucherNumber original voucher number
         * @param reversalVoucherId  reversal voucher ID
         * @param reversalVoucherNumber reversal voucher number
         * @param reason             reversal reason (required)
         * @param reversedByUserId   ID of user who reversed the voucher
         * @param request            HTTP request for IP address and user agent
         */
        void logVoucherReversed(
                        java.util.UUID originalVoucherId,
                        String originalVoucherNumber,
                        java.util.UUID reversalVoucherId,
                        String reversalVoucherNumber,
                        String reason,
                        Long reversedByUserId,
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
        default void logCustomerCreated(
                        Long customerId,
                        String customerCode,
                        Long createdByUserId,
                        HttpServletRequest request) {
                logCustomerCreated(
                                customerId,
                                customerCode,
                                createdByUserId,
                                Collections.singletonMap("code", customerCode),
                                request);
        }

        void logCustomerCreated(
                        Long customerId,
                        String customerCode,
                        Long createdByUserId,
                        Map<String, String> newValues,
                        HttpServletRequest request);

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
                        Long customerId, String customerCode, String reason, Long deletedByUserId,
                        HttpServletRequest request);

        /**
         * Log customer activation. Records customer details, activator, timestamp, and
         * IP address.
         *
         * @param customerId        customer ID
         * @param customerCode      customer code
         * @param activatedByUserId ID of user who activated this customer
         * @param request           HTTP request for IP address and user agent
         */
        void logCustomerActivated(Long customerId, String customerCode, Long activatedByUserId,
                        HttpServletRequest request);

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
        default void logSupplierCreated(
                        Long supplierId, String supplierCode, Long createdByUserId, HttpServletRequest request) {
                logSupplierCreated(
                                supplierId,
                                supplierCode,
                                createdByUserId,
                                Collections.singletonMap("code", supplierCode),
                                request);
        }

        void logSupplierCreated(
                        Long supplierId,
                        String supplierCode,
                        Long createdByUserId,
                        Map<String, String> newValues,
                        HttpServletRequest request);

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
                        Long supplierId, String supplierCode, String reason, Long deletedByUserId,
                        HttpServletRequest request);

        /**
         * Log supplier activation. Records supplier details, activator, timestamp, and
         * IP address.
         *
         * @param supplierId        supplier ID
         * @param supplierCode      supplier code
         * @param activatedByUserId ID of user who activated this supplier
         * @param request           HTTP request for IP address and user agent
         */
        void logSupplierActivated(Long supplierId, String supplierCode, Long activatedByUserId,
                        HttpServletRequest request);

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
        default void logBankAccountCreated(
                        Long bankAccountId, String accountNumber, Long createdByUserId, HttpServletRequest request) {
                logBankAccountCreated(
                                bankAccountId,
                                accountNumber,
                                createdByUserId,
                                Collections.singletonMap("accountNumber", accountNumber),
                                request);
        }

        void logBankAccountCreated(
                        Long bankAccountId,
                        String accountNumber,
                        Long createdByUserId,
                        Map<String, String> newValues,
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
                        Long bankAccountId, String accountNumber, String reason, Long deletedByUserId,
                        HttpServletRequest request);

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

        /**
         * Log bank account import attempt.
         *
         * @param importedCount    number of bank accounts imported successfully
         * @param errorCount       number of errors encountered
         * @param importedByUserId user who performed the import
         * @param request          HTTP request for IP address and user agent
         */
        void logBankAccountImport(int importedCount, int errorCount, Long importedByUserId, HttpServletRequest request);

        /**
         * Log a row-level audit entry for an import attempt.
         *
         * @param companyId      company identifier
         * @param userId         user performing the import
         * @param importType     type of import being executed
         * @param attemptId      unique identifier grouping the import attempt
         * @param sourceFilename original filename supplied by the user
         * @param rowNumber      row number within the template (1-indexed)
         * @param status         outcome status (e.g., SUCCESS, ERROR, ROLLED_BACK,
         *                       VALIDATION_ERROR)
         * @param beforePayload  payload representing the row data before processing
         *                       (may be null)
         * @param afterPayload   payload representing the resulting entity state (may be
         *                       null)
         * @param message        optional descriptive message or error detail
         * @param ipAddress      originating IP address (may be null)
         * @param userAgent      user agent string (may be null)
         */
        void logImportRow(
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
                        String userAgent);

        void logAuditExport(
                        Long companyId,
                        Long userId,
                        int recordCount,
                        String format,
                        String contentHash,
                        HttpServletRequest request);

        void logDataIntegrityScan(
                        UUID jobId,
                        Long companyId,
                        Long userId,
                        int findingsCount,
                        boolean throttled,
                        HttpServletRequest request);

        /**
         * Log fraud detection event. Records user, account, line number, attempted amount,
         * fraud type, timestamp, and IP address.
         *
         * @param userId         ID of user who attempted the fraudulent action
         * @param accountId      account ID involved in the fraud attempt
         * @param accountCode    account code for reference
         * @param lineNumber     line number in voucher where fraud was detected
         * @param attemptedAmount attempted amount (negative value)
         * @param fraudType      type of fraud (e.g., NEGATIVE_AMOUNT, NEGATIVE_DEBIT, NEGATIVE_CREDIT)
         * @param request        HTTP request for IP address and user agent
         */
        void logFraudDetection(
                        Long userId,
                        Long accountId,
                        String accountCode,
                        int lineNumber,
                        java.math.BigDecimal attemptedAmount,
                        String fraudType,
                        HttpServletRequest request);

        /**
         * Log blocked validation attempt. Records user, account, line number, field name,
         * reason, attempt type, timestamp, and IP address.
         *
         * @param userId      ID of user who attempted the blocked action
         * @param accountId   account ID involved in the blocked attempt
         * @param accountCode account code for reference
         * @param lineNumber  line number in voucher where attempt was blocked
         * @param fieldName   field name where the attempt occurred (e.g., debitAccount, creditAccount)
         * @param reason      reason for blocking (error message)
         * @param attemptType type of blocked attempt (e.g., NON_POSTABLE_ACCOUNT, NON_LEAF_ACCOUNT)
         * @param request     HTTP request for IP address and user agent
         */
        void logBlockedAttempt(
                        Long userId,
                        Long accountId,
                        String accountCode,
                        int lineNumber,
                        String fieldName,
                        String reason,
                        String attemptType,
                        HttpServletRequest request);

        /**
         * Log voucher lifecycle event with JSON snapshots and SHA-256 diff hash.
         * Records before/after snapshots, cryptographic hash, user ID/role, device/IP.
         *
         * @param voucherId       voucher ID
         * @param voucherNumber   voucher number for reference
         * @param action          action type (e.g., VOUCHER_CREATED, VOUCHER_UPDATED, VOUCHER_POSTED)
         * @param beforeSnapshot  JSON snapshot of voucher before the change (null for create)
         * @param afterSnapshot   JSON snapshot of voucher after the change
         * @param diffHash        SHA-256 hash of the JSON diff between before/after snapshots
         * @param request        HTTP request for IP address and user agent
         */
        void logVoucherEvent(
                        UUID voucherId,
                        String voucherNumber,
                        String action,
                        com.fasterxml.jackson.databind.JsonNode beforeSnapshot,
                        com.fasterxml.jackson.databind.JsonNode afterSnapshot,
                        String diffHash,
                        HttpServletRequest request);

        /**
         * Log batch/mass voucher action with aggregated entry.
         * Records voucher IDs list, action stats (success/failure counts), start/end timestamp, summary.
         *
         * <p><b>Note:</b> This method is implemented and ready for use, but batch voucher operations
         * (e.g., bulk posting, bulk import, bulk delete) are deferred to post-MVP. When batch
         * operations are implemented, they should call this method to log aggregated audit entries
         * as required by AC2 (Story 3.5).
         *
         * @param voucherIds  list of voucher IDs involved in the batch action
         * @param action      action type (e.g., BATCH_POST, BATCH_DELETE, BATCH_IMPORT)
         * @param stats       batch action statistics (success/failure counts)
         * @param startTime   start timestamp of the batch operation
         * @param endTime     end timestamp of the batch operation
         * @param summary     summary description of the batch action
         * @param request     HTTP request for IP address and user agent
         */
        void logBatchVoucherAction(
                        java.util.List<UUID> voucherIds,
                        String action,
                        BatchActionStats stats,
                        java.time.Instant startTime,
                        java.time.Instant endTime,
                        String summary,
                        HttpServletRequest request);

        /**
         * Log period close event with JSON snapshot and SHA-256 hash.
         * Records period details, close reason, user metadata, hash digest.
         *
         * @param periodId   period ID that was closed
         * @param reason     close reason provided by user
         * @param hashDigest SHA-256 hash of the period snapshot for integrity verification
         */
        void logPeriodClosed(UUID periodId, String reason, String hashDigest);

        /**
         * Log period reopen event with approval metadata and SHA-256 hash.
         * Records period details, reopen reason, approval metadata, hash digest.
         * Logs all reopen attempts (approved and rejected) for compliance.
         *
         * @param periodId         period ID that was reopened
         * @param reason           reopen reason provided by user
         * @param approvalMetadata approval metadata (approver, approval reference, etc.)
         * @param hashDigest       SHA-256 hash of the period snapshot for integrity verification
         */
        void logPeriodReopened(UUID periodId, String reason, String approvalMetadata, String hashDigest);

        /**
         * Log period validation blocked attempt.
         * Records user, period, operation, reason, timestamp for audit trail.
         *
         * @param periodId period ID where operation was blocked
         * @param operation operation that was blocked (e.g., VOUCHER_CREATE, VOUCHER_POST)
         * @param reason   reason for blocking (e.g., PERIOD_CLOSED, PERIOD_FUTURE)
         */
        void logPeriodValidationBlocked(UUID periodId, String operation, String reason);

        /**
         * Log attachment download event. Records attachment ID, voucher ID, file metadata,
         * user ID, timestamp, and IP address.
         *
         * @param attachmentId attachment ID that was downloaded
         * @param voucherId voucher ID that the attachment belongs to
         * @param fileName file name of the attachment
         * @param fileSize file size in bytes
         * @param mimeType MIME type of the file
         * @param downloadedByUserId ID of user who downloaded the attachment
         * @param request HTTP request for IP address and user agent
         */
        void logAttachmentDownload(
                        UUID attachmentId,
                        UUID voucherId,
                        String fileName,
                        Long fileSize,
                        String mimeType,
                        Long downloadedByUserId,
                        HttpServletRequest request);

        /**
         * Log attachment view/preview event. Records attachment ID, voucher ID, file metadata,
         * user ID, timestamp, and IP address.
         *
         * @param attachmentId attachment ID that was viewed
         * @param voucherId voucher ID that the attachment belongs to
         * @param fileName file name of the attachment
         * @param fileSize file size in bytes
         * @param mimeType MIME type of the file
         * @param viewedByUserId ID of user who viewed the attachment
         * @param request HTTP request for IP address and user agent
         */
        void logAttachmentView(
                        UUID attachmentId,
                        UUID voucherId,
                        String fileName,
                        Long fileSize,
                        String mimeType,
                        Long viewedByUserId,
                        HttpServletRequest request);

        /**
         * Log attachment delete event. Records attachment ID, voucher ID, file metadata,
         * deletion reason, user ID, timestamp, and IP address.
         *
         * @param attachmentId attachment ID that was deleted
         * @param voucherId voucher ID that the attachment belongs to
         * @param fileName file name of the attachment
         * @param fileSize file size in bytes
         * @param mimeType MIME type of the file
         * @param reason deletion reason (required for audit)
         * @param deletedByUserId ID of user who deleted the attachment
         * @param request HTTP request for IP address and user agent
         */
        void logAttachmentDelete(
                        UUID attachmentId,
                        UUID voucherId,
                        String fileName,
                        Long fileSize,
                        String mimeType,
                        String reason,
                        Long deletedByUserId,
                        HttpServletRequest request);

        /**
         * DTO for batch action statistics.
         */
        class BatchActionStats {
                private final int successCount;
                private final int failureCount;
                private final int totalCount;

                public BatchActionStats(int successCount, int failureCount) {
                        this.successCount = successCount;
                        this.failureCount = failureCount;
                        this.totalCount = successCount + failureCount;
                }

                public int getSuccessCount() {
                        return successCount;
                }

                public int getFailureCount() {
                        return failureCount;
                }

                public int getTotalCount() {
                        return totalCount;
                }
        }
}
