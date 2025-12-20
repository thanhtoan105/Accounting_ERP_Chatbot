package com.accounting.util;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.accounting.service.DemoBootstrapService;

/**
 * CLI trigger to bootstrap demo data.
 * Run with: mvnd spring-boot:run -Dspring-boot.run.arguments=--seedDemo=true
 */
@Component
public class DemoBootstrapRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoBootstrapRunner.class);

  private final DemoBootstrapService demoBootstrapService;

  @Value("${seedDemo:false}")
  private boolean seedDemo;

  public DemoBootstrapRunner(DemoBootstrapService demoBootstrapService) {
    this.demoBootstrapService = demoBootstrapService;
  }

  @Override
  public void run(String... args) {
    if (!seedDemo) {
      return;
    }
    Map<String, Object> out = demoBootstrapService.bootstrapDemoCompany();
    log.info("[DEMO] Bootstrap executed. created={}, companyId={}, code={}", out.get("created"), out.get("companyId"), out.get("code"));
  }
}
