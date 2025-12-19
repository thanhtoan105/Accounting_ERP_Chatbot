package com.accounting.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import com.accounting.entity.VatRate;

public final class MoneyHelper {

  private MoneyHelper() {}

  public static BigDecimal roundVnd(BigDecimal amount) {
    return amount.setScale(0, RoundingMode.HALF_UP);
  }

  public static BigDecimal calculateVat(BigDecimal amount, VatRate rate) {
    return roundVnd(amount.multiply(rate.getRate()));
  }

  public static BigDecimal niceVndAmount(Random random, long min, long max) {
    long minUnits = min / 10_000;
    long maxUnits = max / 10_000;
    long units = minUnits + (long) (random.nextDouble() * (maxUnits - minUnits + 1));
    return new BigDecimal(units * 10_000);
  }

  public static VatRate randomVatRate(Random random) {
    int roll = random.nextInt(100);
    if (roll < 15) {
      return VatRate.ZERO;
    } else if (roll < 30) {
      return VatRate.FIVE;
    } else if (roll < 90) {
      return VatRate.TEN;
    } else {
      return VatRate.EXEMPT;
    }
  }
}
