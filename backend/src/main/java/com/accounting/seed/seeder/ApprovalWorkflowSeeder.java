package com.accounting.seed.seeder;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.ApprovalWorkflow;
import com.accounting.entity.ApprovalWorkflowStatus;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.entity.SalesInvoiceStatus;
import com.accounting.seed.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class ApprovalWorkflowSeeder {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO approval_workflows (id, company_id, purchase_bill_id, sales_invoice_id, "
                    + "created_by_id, approved_by_id, status, threshold_amount, bill_amount, is_sensitive, "
                    + "approval_reason, rejection_reason, created_at, updated_at, approved_at, rejected_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final List<String> APPROVAL_REASONS =
            List.of("Đã duyệt theo quy trình", "Phù hợp ngân sách");

    private static final List<String> REJECTION_REASONS =
            List.of("Vượt ngân sách", "Cần bổ sung chứng từ");

    private static final String UPDATE_BILL_STATUS_SQL =
            "UPDATE purchase_bills SET status = ?, approved_by_id = ?, updated_at = ? WHERE id = ?";

    private static final String UPDATE_INVOICE_STATUS_SQL =
            "UPDATE sales_invoices SET status = ?, approved_by_id = ?, updated_at = ? WHERE id = ?";

    @Transactional
    public List<ApprovalWorkflow> seedApprovalWorkflows(
            TenantContext ctx,
            Map<UUID, PurchaseBillSeeder.DocumentInfo> billInfo,
            Map<UUID, SalesInvoiceSeeder.DocumentInfo> invoiceInfo,
            Random random) {
        validatePreconditions(ctx);
        log.info(
                "Seeding approval workflows for company ID: {} (bills: {}, invoices: {})",
                ctx.getCompanyId(),
                billInfo.size(),
                invoiceInfo.size());

        List<WorkflowData> workflows = new ArrayList<>();
        List<DocumentStatusUpdate> billUpdates = new ArrayList<>();
        List<DocumentStatusUpdate> invoiceUpdates = new ArrayList<>();
        Instant now = Instant.now();

        for (var entry : billInfo.entrySet()) {
            UUID billId = entry.getKey();
            PurchaseBillSeeder.DocumentInfo doc = entry.getValue();
            if (doc.status() == PurchaseBillStatus.PENDING_APPROVAL) {
                WorkflowData wf = createWorkflowForBill(ctx, billId, doc, random, now);
                workflows.add(wf);
                billUpdates.add(new DocumentStatusUpdate(
                        billId,
                        mapWorkflowToBillStatus(wf.status),
                        wf.approvedById,
                        now));
            }
        }

        for (var entry : invoiceInfo.entrySet()) {
            UUID invoiceId = entry.getKey();
            SalesInvoiceSeeder.DocumentInfo doc = entry.getValue();
            if (doc.status() == SalesInvoiceStatus.PENDING_APPROVAL) {
                WorkflowData wf = createWorkflowForInvoice(ctx, invoiceId, doc, random, now);
                workflows.add(wf);
                invoiceUpdates.add(new DocumentStatusUpdate(
                        invoiceId,
                        mapWorkflowToInvoiceStatus(wf.status),
                        wf.approvedById,
                        now));
            }
        }

        if (!workflows.isEmpty()) {
            batchInsertWorkflows(workflows);
        }

        if (!billUpdates.isEmpty()) {
            batchUpdateBillStatuses(billUpdates);
        }

        if (!invoiceUpdates.isEmpty()) {
            batchUpdateInvoiceStatuses(invoiceUpdates);
        }

        log.info(
                "Successfully seeded {} approval workflows for company ID: {} (updated {} bills, {} invoices)",
                workflows.size(),
                ctx.getCompanyId(),
                billUpdates.size(),
                invoiceUpdates.size());

        return workflows.stream().map(this::toEntity).toList();
    }

    private WorkflowData createWorkflowForBill(
            TenantContext ctx,
            UUID purchaseBillId,
            PurchaseBillSeeder.DocumentInfo doc,
            Random random,
            Instant now) {
        UUID id = UUID.randomUUID();
        Long companyId = ctx.getCompanyId();
        Long createdById = randomElement(ctx.getUserIds(), random);

        ApprovalWorkflowStatus status = randomStatus(random);
        Long approvedById = (status == ApprovalWorkflowStatus.APPROVED)
                ? randomOther(ctx.getUserIds(), createdById, random)
                : null;

        BigDecimal thresholdAmount = randomThresholdAmount(random);
        BigDecimal billAmount = doc.totalAmount();
        boolean isSensitive = random.nextDouble() < 0.10;

        String approvalReason = null;
        String rejectionReason = null;
        Instant approvedAt = null;
        Instant rejectedAt = null;
        Instant createdAt = now.minus(random.nextInt(30), ChronoUnit.DAYS);

        if (status == ApprovalWorkflowStatus.APPROVED) {
            approvalReason = randomElement(APPROVAL_REASONS, random);
            approvedAt = createdAt.plus(random.nextInt(48) + 1, ChronoUnit.HOURS);
        } else if (status == ApprovalWorkflowStatus.REJECTED) {
            rejectionReason = randomElement(REJECTION_REASONS, random);
            rejectedAt = createdAt.plus(random.nextInt(48) + 1, ChronoUnit.HOURS);
        }

        return new WorkflowData(
                id,
                companyId,
                purchaseBillId,
                null,
                createdById,
                approvedById,
                status,
                thresholdAmount,
                billAmount,
                isSensitive,
                approvalReason,
                rejectionReason,
                createdAt,
                createdAt,
                approvedAt,
                rejectedAt);
    }

    private WorkflowData createWorkflowForInvoice(
            TenantContext ctx,
            UUID salesInvoiceId,
            SalesInvoiceSeeder.DocumentInfo doc,
            Random random,
            Instant now) {
        UUID id = UUID.randomUUID();
        Long companyId = ctx.getCompanyId();
        Long createdById = randomElement(ctx.getUserIds(), random);

        ApprovalWorkflowStatus status = randomStatus(random);
        Long approvedById = (status == ApprovalWorkflowStatus.APPROVED)
                ? randomOther(ctx.getUserIds(), createdById, random)
                : null;

        BigDecimal thresholdAmount = randomThresholdAmount(random);
        BigDecimal billAmount = doc.totalAmount();
        boolean isSensitive = random.nextDouble() < 0.10;

        String approvalReason = null;
        String rejectionReason = null;
        Instant approvedAt = null;
        Instant rejectedAt = null;
        Instant createdAt = now.minus(random.nextInt(30), ChronoUnit.DAYS);

        if (status == ApprovalWorkflowStatus.APPROVED) {
            approvalReason = randomElement(APPROVAL_REASONS, random);
            approvedAt = createdAt.plus(random.nextInt(48) + 1, ChronoUnit.HOURS);
        } else if (status == ApprovalWorkflowStatus.REJECTED) {
            rejectionReason = randomElement(REJECTION_REASONS, random);
            rejectedAt = createdAt.plus(random.nextInt(48) + 1, ChronoUnit.HOURS);
        }

        return new WorkflowData(
                id,
                companyId,
                null,
                salesInvoiceId,
                createdById,
                approvedById,
                status,
                thresholdAmount,
                billAmount,
                isSensitive,
                approvalReason,
                rejectionReason,
                createdAt,
                createdAt,
                approvedAt,
                rejectedAt);
    }

    private String mapWorkflowToBillStatus(ApprovalWorkflowStatus wfStatus) {
        return switch (wfStatus) {
            case APPROVED, AUTO_APPROVED -> PurchaseBillStatus.POSTED.name();
            case REJECTED -> PurchaseBillStatus.REJECTED.name();
            case PENDING -> PurchaseBillStatus.PENDING_APPROVAL.name();
        };
    }

    private String mapWorkflowToInvoiceStatus(ApprovalWorkflowStatus wfStatus) {
        return switch (wfStatus) {
            case APPROVED, AUTO_APPROVED -> SalesInvoiceStatus.POSTED.name();
            case REJECTED -> SalesInvoiceStatus.REJECTED.name();
            case PENDING -> SalesInvoiceStatus.PENDING_APPROVAL.name();
        };
    }

    private ApprovalWorkflowStatus randomStatus(Random random) {
        int roll = random.nextInt(100);
        if (roll < 20) return ApprovalWorkflowStatus.PENDING;
        if (roll < 90) return ApprovalWorkflowStatus.APPROVED;
        return ApprovalWorkflowStatus.REJECTED;
    }

    private BigDecimal randomThresholdAmount(Random random) {
        long amount = 10_000_000L + (long) (random.nextDouble() * 90_000_000L);
        amount = (amount / 100_000) * 100_000;
        return BigDecimal.valueOf(amount);
    }

    private record DocumentStatusUpdate(UUID docId, String newStatus, Long approvedById, Instant updatedAt) {}

    private void batchUpdateBillStatuses(List<DocumentStatusUpdate> updates) {
        jdbcTemplate.batchUpdate(
                UPDATE_BILL_STATUS_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        DocumentStatusUpdate u = updates.get(i);
                        ps.setString(1, u.newStatus);
                        setNullableLong(ps, 2, u.approvedById);
                        ps.setTimestamp(3, Timestamp.from(u.updatedAt));
                        ps.setObject(4, u.docId);
                    }

                    @Override
                    public int getBatchSize() {
                        return updates.size();
                    }
                });
    }

    private void batchUpdateInvoiceStatuses(List<DocumentStatusUpdate> updates) {
        jdbcTemplate.batchUpdate(
                UPDATE_INVOICE_STATUS_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        DocumentStatusUpdate u = updates.get(i);
                        ps.setString(1, u.newStatus);
                        setNullableLong(ps, 2, u.approvedById);
                        ps.setTimestamp(3, Timestamp.from(u.updatedAt));
                        ps.setObject(4, u.docId);
                    }

                    @Override
                    public int getBatchSize() {
                        return updates.size();
                    }
                });
    }

    private <T> T randomElement(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    private Long randomOther(List<Long> list, Long exclude, Random random) {
        if (list.size() <= 1) {
            return list.get(0);
        }
        Long result;
        do {
            result = randomElement(list, random);
        } while (result.equals(exclude));
        return result;
    }

    private void batchInsertWorkflows(List<WorkflowData> workflows) {
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        WorkflowData w = workflows.get(i);
                        ps.setObject(1, w.id);
                        ps.setLong(2, w.companyId);
                        setNullableUUID(ps, 3, w.purchaseBillId);
                        setNullableUUID(ps, 4, w.salesInvoiceId);
                        ps.setLong(5, w.createdById);
                        setNullableLong(ps, 6, w.approvedById);
                        ps.setString(7, w.status.name());
                        ps.setBigDecimal(8, w.thresholdAmount);
                        ps.setBigDecimal(9, w.billAmount);
                        ps.setBoolean(10, w.isSensitive);
                        ps.setString(11, w.approvalReason);
                        ps.setString(12, w.rejectionReason);
                        ps.setTimestamp(13, Timestamp.from(w.createdAt));
                        ps.setTimestamp(14, Timestamp.from(w.updatedAt));
                        setNullableTimestamp(ps, 15, w.approvedAt);
                        setNullableTimestamp(ps, 16, w.rejectedAt);
                    }

                    @Override
                    public int getBatchSize() {
                        return workflows.size();
                    }
                });
    }

    private void setNullableUUID(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value != null) {
            ps.setObject(index, value);
        } else {
            ps.setNull(index, Types.OTHER);
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private void setNullableTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.from(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }

    private ApprovalWorkflow toEntity(WorkflowData data) {
        ApprovalWorkflow entity = new ApprovalWorkflow();
        entity.setId(data.id);
        entity.setCompanyId(data.companyId);
        entity.setPurchaseBillId(data.purchaseBillId);
        entity.setSalesInvoiceId(data.salesInvoiceId);
        entity.setCreatedById(data.createdById);
        entity.setApprovedById(data.approvedById);
        entity.setStatus(data.status);
        entity.setThresholdAmount(data.thresholdAmount);
        entity.setBillAmount(data.billAmount);
        entity.setIsSensitive(data.isSensitive);
        entity.setApprovalReason(data.approvalReason);
        entity.setRejectionReason(data.rejectionReason);
        entity.setCreatedAt(data.createdAt);
        entity.setUpdatedAt(data.updatedAt);
        entity.setApprovedAt(data.approvedAt);
        entity.setRejectedAt(data.rejectedAt);
        return entity;
    }

    private record WorkflowData(
            UUID id,
            Long companyId,
            UUID purchaseBillId,
            UUID salesInvoiceId,
            Long createdById,
            Long approvedById,
            ApprovalWorkflowStatus status,
            BigDecimal thresholdAmount,
            BigDecimal billAmount,
            boolean isSensitive,
            String approvalReason,
            String rejectionReason,
            Instant createdAt,
            Instant updatedAt,
            Instant approvedAt,
            Instant rejectedAt) {}

    private void validatePreconditions(TenantContext ctx) {
        if (ctx.getUserIds() == null || ctx.getUserIds().isEmpty()) {
            throw new IllegalStateException(
                    "Cannot seed ApprovalWorkflows: userIds is empty. Seed users first.");
        }
    }
}
