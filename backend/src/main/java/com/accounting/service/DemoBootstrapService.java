package com.accounting.service;

import java.util.Map;

public interface DemoBootstrapService {
  /**
   * Bootstrap a demo company and minimal reference data.
   *
   * <p>Idempotent: safe to call multiple times. Returns map including keys: created (boolean),
   * companyId (Long), code (String).
   */
  Map<String, Object> bootstrapDemoCompany();
}
