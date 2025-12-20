package com.accounting.seed;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UniqueGenerator {

  private final long baseSeed;
  private final Random random;
  private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

  public UniqueGenerator(long baseSeed) {
    this.baseSeed = baseSeed;
    this.random = new Random(baseSeed);
  }

  private int nextCounter(String key) {
    return counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
  }

  public String nextCompanyCode() {
    int seq = nextCounter("company");
    return String.format("DEMO-%02d", seq);
  }

  public String nextTaxCode(Set<String> existing) {
    String taxCode;
    do {
      int provinceCode = random.nextInt(10);
      long remaining = (long) (random.nextDouble() * 1_000_000_000L);
      taxCode = String.format("%d%09d", provinceCode, remaining);
    } while (existing.contains(taxCode));
    existing.add(taxCode);
    return taxCode;
  }

  public String nextVoucherNumber(Long companyId, int year, String prefix) {
    String key = String.format("%d:voucher:%s:%d", companyId, prefix, year);
    int seq = nextCounter(key);
    return String.format("%s-%d-%04d", prefix, year, seq);
  }

  public String nextBillNumber(Long companyId, Long supplierId, int year) {
    String key = String.format("%d:bill:%d:%d", companyId, supplierId, year);
    int seq = nextCounter(key);
    return String.format("HDM-%d-%04d", year, seq);
  }

  public String nextInvoiceNumber(Long companyId, Long customerId, int year) {
    String key = String.format("%d:invoice:%d:%d", companyId, customerId, year);
    int seq = nextCounter(key);
    return String.format("INV-%d-%04d", year, seq);
  }

  public String nextCustomerCode(Long companyId, int year) {
    String key = String.format("%d:customer:%d", companyId, year);
    int seq = nextCounter(key);
    return String.format("KH-%d-%04d", year, seq);
  }

  public String nextSupplierCode(Long companyId, int year) {
    String key = String.format("%d:supplier:%d", companyId, year);
    int seq = nextCounter(key);
    return String.format("NCC-%d-%04d", year, seq);
  }

  public String nextPaymentNumber(Long companyId, int year, String prefix) {
    String key = String.format("%d:payment:%s:%d", companyId, prefix, year);
    int seq = nextCounter(key);
    return String.format("%s-%d-%04d", prefix, year, seq);
  }

  public String nextBankAccountNumber(Long companyId) {
    String key = String.format("%d:bankaccount", companyId);
    int seq = nextCounter(key);
    long base = baseSeed % 1_000_000_000L;
    return String.format("%09d%05d", base, seq);
  }
}
