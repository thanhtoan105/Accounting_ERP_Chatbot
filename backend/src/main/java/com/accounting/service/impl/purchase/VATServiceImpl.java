package com.accounting.service.impl.purchase;

import com.accounting.entity.VatRate;
import com.accounting.service.purchase.VATService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of VATService for purchase bills.
 * Handles VAT calculation, validation, and GL mapping for TT200 compliance.
 */
@Service("purchaseVatServiceImpl")
public class VATServiceImpl implements VATService {

  private static final Logger logger = LoggerFactory.getLogger(VATServiceImpl.class);

  // VAT sum tolerance: 1,000₫ (for rounding differences)
  private static final BigDecimal VAT_TOLERANCE = new BigDecimal("1000.00");

  // VAT GL account code (TT200 compliance)
  private static final String VAT_GL_ACCOUNT_CODE = "3331";

  // Number of decimal places for calculations
  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  @Override
  public BigDecimal calculateVAT(BigDecimal amount, VatRate rate) {
    if (amount == null) {
      amount = BigDecimal.ZERO;
    }
    if (rate == null) {
      rate = VatRate.ZERO;
    }

    // For EXEMPT, VAT is always 0
    if (rate == VatRate.EXEMPT) {
      return BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
    }

    // Calculate VAT: amount × rate
    BigDecimal vatAmount = amount.multiply(rate.getRate()).setScale(SCALE, ROUNDING_MODE);

    logger.debug("Calculated VAT: amount={}, rate={}, vatAmount={}", amount, rate, vatAmount);

    return vatAmount;
  }

  @Override
  public boolean validateVATSum(BigDecimal headerVAT, BigDecimal lineVATSum) {
    BigDecimal difference = getVATSumDifference(headerVAT, lineVATSum);
    boolean isValid = difference.compareTo(VAT_TOLERANCE) <= 0;

    if (!isValid) {
      logger.warn("VAT sum mismatch: headerVAT={}, lineVATSum={}, difference={}, tolerance={}",
          headerVAT, lineVATSum, difference, VAT_TOLERANCE);
    }

    return isValid;
  }

  @Override
  public BigDecimal getVATSumDifference(BigDecimal headerVAT, BigDecimal lineVATSum) {
    if (headerVAT == null) {
      headerVAT = BigDecimal.ZERO;
    }
    if (lineVATSum == null) {
      lineVATSum = BigDecimal.ZERO;
    }

    // Calculate absolute difference
    BigDecimal difference = headerVAT.subtract(lineVATSum).abs();

    return difference.setScale(SCALE, ROUNDING_MODE);
  }

  @Override
  public String mapToGL(BigDecimal vatAmount) {
    if (vatAmount == null || vatAmount.compareTo(BigDecimal.ZERO) == 0) {
      // No VAT amount, return null or empty (no GL entry needed)
      return null;
    }

    // TT200 compliance: VAT payable is mapped to account 3331
    logger.debug("Mapping VAT amount {} to GL account {}", vatAmount, VAT_GL_ACCOUNT_CODE);

    return VAT_GL_ACCOUNT_CODE;
  }

  @Override
  public BigDecimal getVATTolerance() {
    return VAT_TOLERANCE;
  }
}

