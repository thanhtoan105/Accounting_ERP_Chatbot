package com.accounting.aspect;

import com.accounting.annotation.PeriodProtected;
import com.accounting.dto.AccountingPeriodDTO;
import com.accounting.entity.AuditLog;
import com.accounting.enums.CashBankAuditAction;
import com.accounting.exception.BusinessException;
import com.accounting.repository.AuditLogRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.PeriodManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Aspect that enforces period protection on annotated methods.
 *
 * <p>When a method is annotated with {@link PeriodProtected}, this aspect:
 * <ol>
 *   <li>Extracts the transaction date from method parameters</li>
 *   <li>Checks if the date falls within an open accounting period</li>
 *   <li>If closed, logs a {@code PERIOD_BLOCK_ATTEMPT} audit entry</li>
 *   <li>Throws {@link BusinessException} with code "PERIOD_CLOSED"</li>
 * </ol>
 *
 * @see PeriodProtected
 */
@Aspect
@Component
@Order(100) // Run after security/validation aspects
public class PeriodProtectionAspect {

  private static final Logger logger = LoggerFactory.getLogger(PeriodProtectionAspect.class);

  private final PeriodManagementService periodManagementService;
  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public PeriodProtectionAspect(
      PeriodManagementService periodManagementService,
      AuditLogRepository auditLogRepository,
      ObjectMapper objectMapper) {
    this.periodManagementService = periodManagementService;
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  @Before("@annotation(periodProtected)")
  public void checkPeriodProtection(JoinPoint joinPoint, PeriodProtected periodProtected) {
    String dateParam = periodProtected.dateParam();
    String operation = periodProtected.operation();
    String entityType = periodProtected.entityType();

    // Extract the date from method parameters
    LocalDate transactionDate = extractDate(joinPoint, dateParam);

    if (transactionDate == null) {
      logger.warn("Could not extract date from parameter '{}' for method {}, skipping period check",
          dateParam, joinPoint.getSignature().getName());
      return;
    }

    // Derive entity type from method name if not specified
    if (entityType == null || entityType.isEmpty()) {
      entityType = deriveEntityType(joinPoint);
    }

    // Check if date is in an open period
    Optional<AccountingPeriodDTO> period = periodManagementService.findPeriodByDate(transactionDate);

    if (period.isEmpty()) {
      // No period found for this date - might be acceptable depending on business rules
      logger.debug("No period found for date {} in operation {}", transactionDate, operation);
      return;
    }

    boolean isOpen = periodManagementService.isPeriodOpen(period.get().getId());

    if (!isOpen) {
      // Period is closed - log the blocked attempt and throw exception
      String periodName = period.get().getPeriodName();
      LocalDate startDate = period.get().getStartDate();
      LocalDate endDate = period.get().getEndDate();

      logBlockedAttempt(operation, entityType, transactionDate, period.get());

      String message = String.format(
          "Cannot modify transactions in closed period: %s (%s - %s)",
          periodName, startDate, endDate);

      logger.warn("Period protection blocked operation '{}' for entity '{}' - {}",
          operation, entityType, message);

      throw new BusinessException(message, "PERIOD_CLOSED");
    }
  }

  /**
   * Extract date from method parameters using the specified path.
   */
  private LocalDate extractDate(JoinPoint joinPoint, String dateParam) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] args = joinPoint.getArgs();

    // Handle nested path (e.g., "dto.voucherDate")
    if (dateParam.contains(".")) {
      String[] parts = dateParam.split("\\.", 2);
      String paramName = parts[0];
      String propertyPath = parts[1];

      for (int i = 0; i < parameters.length; i++) {
        if (parameters[i].getName().equals(paramName) && args[i] != null) {
          try {
            BeanWrapper wrapper = new BeanWrapperImpl(args[i]);
            Object value = wrapper.getPropertyValue(propertyPath);
            if (value instanceof LocalDate date) {
              return date;
            }
          } catch (Exception e) {
            logger.debug("Could not extract property '{}' from parameter '{}'",
                propertyPath, paramName, e);
          }
        }
      }
    } else {
      // Direct parameter name
      for (int i = 0; i < parameters.length; i++) {
        if (parameters[i].getName().equals(dateParam)) {
          if (args[i] instanceof LocalDate date) {
            return date;
          }
        }
      }

      // Try to find any LocalDate parameter if name doesn't match
      // (parameter names might not be preserved in bytecode)
      for (int i = 0; i < args.length; i++) {
        if (args[i] instanceof LocalDate date) {
          return date;
        }
      }
    }

    return null;
  }

  /**
   * Derive entity type from method name (e.g., "createReceipt" -> "Receipt").
   */
  private String deriveEntityType(JoinPoint joinPoint) {
    String methodName = joinPoint.getSignature().getName();

    // Common patterns: createXxx, updateXxx, deleteXxx, postXxx
    String[] prefixes = {"create", "update", "delete", "post", "save", "modify"};
    for (String prefix : prefixes) {
      if (methodName.startsWith(prefix) && methodName.length() > prefix.length()) {
        return methodName.substring(prefix.length());
      }
    }

    return joinPoint.getSignature().getDeclaringType().getSimpleName()
        .replace("Service", "")
        .replace("Impl", "");
  }

  /**
   * Log a PERIOD_BLOCK_ATTEMPT audit entry.
   */
  private void logBlockedAttempt(String operation, String entityType,
      LocalDate transactionDate, AccountingPeriodDTO period) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("operation", operation);
      metadata.put("transactionDate", transactionDate.toString());
      metadata.put("periodId", period.getId().toString());
      metadata.put("periodName", period.getPeriodName());
      metadata.put("periodStartDate", period.getStartDate().toString());
      metadata.put("periodEndDate", period.getEndDate().toString());
      metadata.put("reason", "Attempted operation on closed period");

      Long userId = null;
      String email = null;
      String ipAddress = null;

      try {
        userId = SecurityUtils.getCurrentUserId();
        email = SecurityUtils.getCurrentUserEmail();
        ipAddress = SecurityUtils.getCurrentIpAddress();
      } catch (Exception e) {
        logger.debug("Could not get current user info for audit log", e);
      }

      AuditLog log = new AuditLog();
      log.setAction(CashBankAuditAction.PERIOD_BLOCK_ATTEMPT.getValue());
      log.setEventType("CASH_BANK_AUDIT");
      log.setCompanyId(CompanyContext.getCompanyId());
      log.setEntityType(entityType);
      log.setUserId(userId);
      log.setEmail(email);
      log.setIpAddress(ipAddress);
      log.setMetadata(metadata);
      log.setSuccess(false);
      log.setFailureReason("Period closed");
      log.setCreatedAt(Instant.now());

      auditLogRepository.save(log);

      logger.info("Logged PERIOD_BLOCK_ATTEMPT for {} operation on {}", operation, entityType);
    } catch (Exception e) {
      logger.error("Failed to log period block attempt", e);
    }
  }
}
