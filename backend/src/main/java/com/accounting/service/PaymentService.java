package com.accounting.service;

import com.accounting.dto.APPaymentCreateRequest;
import com.accounting.dto.APPaymentDTO;
import com.accounting.dto.APPaymentListDTO;
import com.accounting.dto.PaymentAllocationDTO;
import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for managing AP payments including creation, allocation, posting, and
 * cancellation.
 */
public interface PaymentService {

  /**
   * Find all payments with pagination, sorting, and filtering.
   *
   * @param pageable   pagination and sorting parameters
   * @param supplierId filter by supplier ID (optional)
   * @param status     filter by status (optional)
   * @param dateFrom   filter by date from (optional)
   * @param dateTo     filter by date to (optional)
   * @param search     search term for payment number, reference, or payee
   *                   (optional)
   * @param standalone filter by standalone flag (optional)
   * @return paginated list of payments
   */
  Page<APPaymentListDTO> findAll(
      Pageable pageable,
      Long supplierId,
      PaymentStatus status,
      LocalDate dateFrom,
      LocalDate dateTo,
      String search,
      Boolean standalone);

  /**
   * Get payment by ID.
   *
   * @param paymentId payment ID
   * @return optional payment DTO with allocations
   */
  Optional<APPaymentDTO> findById(UUID paymentId);

  /**
   * Create a new payment with optional allocations.
   * If allocations not provided, performs FIFO allocation automatically.
   *
   * @param request payment create request
   * @return created payment DTO
   */
  APPaymentDTO create(APPaymentCreateRequest request);

  /**
   * Update an existing draft payment.
   *
   * @param paymentId payment ID
   * @param request   payment update request
   * @return updated payment DTO
   */
  APPaymentDTO update(UUID paymentId, APPaymentCreateRequest request);

  /**
   * Delete a draft payment.
   *
   * @param paymentId payment ID
   */
  void delete(UUID paymentId);

  /**
   * Allocate payment manually (override FIFO allocation).
   *
   * @param paymentId   payment ID
   * @param allocations list of allocation requests
   * @return updated payment DTO
   */
  APPaymentDTO allocateManually(UUID paymentId, List<PaymentAllocationRequest> allocations);

  /**
   * Allocate payment using FIFO algorithm.
   * Fetches open/unpaid bills sorted by due_date ASC and allocates payment
   * amount.
   *
   * @param paymentAmount total payment amount
   * @param supplierId    supplier ID
   * @return list of allocation DTOs
   */
  List<PaymentAllocationDTO> allocateFIFO(BigDecimal paymentAmount, Long supplierId);

  /**
   * Post payment (generate voucher, update bill statuses, update balances).
   *
   * @param paymentId payment ID
   * @return posted payment DTO
   */
  APPaymentDTO postPayment(UUID paymentId);

  /**
   * Cancel a draft payment.
   *
   * @param paymentId payment ID
   * @return cancelled payment DTO
   */
  APPaymentDTO cancelPayment(UUID paymentId);

  /**
   * Get open/unpaid bills for a supplier.
   * Returns bills with status=POSTED and remaining_balance > 0.
   *
   * @param supplierId supplier ID
   * @return list of purchase bill DTOs
   */
  List<com.accounting.dto.PurchaseBillDTO> getOpenBillsForSupplier(Long supplierId);

  /**
   * Approve a payment that is pending approval.
   * AC6.3-08: Validates approver role (CHIEF_ACCOUNTANT/CFO/ADMIN) and
   * maker-checker pattern.
   *
   * @param paymentId payment ID
   * @return approved payment DTO
   */
  APPaymentDTO approvePayment(UUID paymentId);

  /**
   * Reject a payment that is pending approval.
   * AC6.3-08: Validates approver role (CHIEF_ACCOUNTANT/CFO/ADMIN).
   *
   * @param paymentId payment ID
   * @param reason    rejection reason
   * @return rejected payment DTO
   */
  APPaymentDTO rejectPayment(UUID paymentId, String reason);

  /**
   * Reverse a posted payment.
   * AC6.3-10: Creates reversing voucher and updates bill/allocation states.
   *
   * @param paymentId payment ID
   * @param reason    reversal reason (mandatory)
   * @return reversed payment DTO
   */
  APPaymentDTO reversePayment(UUID paymentId, String reason);
}
