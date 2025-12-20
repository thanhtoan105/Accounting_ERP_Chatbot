package com.accounting.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to protect methods from operating on closed accounting periods.
 *
 * <p>When applied to a method, the {@link com.accounting.aspect.PeriodProtectionAspect}
 * will:
 * <ol>
 *   <li>Extract the transaction date from method parameters</li>
 *   <li>Check if the date falls within an open accounting period</li>
 *   <li>If closed, log a {@code PERIOD_BLOCK_ATTEMPT} audit entry</li>
 *   <li>Throw {@link com.accounting.exception.BusinessException} with code "PERIOD_CLOSED"</li>
 * </ol>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * @PeriodProtected(dateParam = "voucherDate", operation = "CREATE_RECEIPT")
 * public CashReceiptDTO createReceipt(CashReceiptCreateDTO dto) {
 *     // Method only executes if voucherDate is in an open period
 * }
 * }</pre>
 *
 * <h3>Parameter Resolution:</h3>
 * The aspect looks for the date parameter in this order:
 * <ol>
 *   <li>If {@code dateParam} matches a parameter name of type {@code LocalDate}</li>
 *   <li>If {@code dateParam} is a nested path like "dto.voucherDate", uses reflection</li>
 *   <li>If no match found, logs a warning and skips validation</li>
 * </ol>
 *
 * @see com.accounting.aspect.PeriodProtectionAspect
 * @see com.accounting.enums.CashBankAuditAction#PERIOD_BLOCK_ATTEMPT
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PeriodProtected {

  /**
   * The parameter name or path containing the transaction date.
   *
   * <p>Examples:
   * <ul>
   *   <li>"voucherDate" - direct parameter named voucherDate</li>
   *   <li>"dto.voucherDate" - nested property in a DTO parameter</li>
   *   <li>"request.transactionDate" - nested property in request object</li>
   * </ul>
   *
   * @return the parameter name or nested path
   */
  String dateParam();

  /**
   * The operation type for audit logging.
   *
   * <p>Examples: "CREATE_RECEIPT", "UPDATE_PAYMENT", "POST_VOUCHER", "DELETE_RECONCILIATION"
   *
   * @return the operation type identifier
   */
  String operation();

  /**
   * The entity type for audit logging (optional, derived from method name if not specified).
   *
   * @return the entity type
   */
  String entityType() default "";
}
