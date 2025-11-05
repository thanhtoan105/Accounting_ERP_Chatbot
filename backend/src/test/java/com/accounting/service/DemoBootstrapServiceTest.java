package com.accounting.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class DemoBootstrapServiceTest extends com.accounting.test.IntegrationTest {

    @Autowired
    private DemoBootstrapService demoBootstrapService;

    @Test
    @Transactional
    void bootstrapIsIdempotent() {
        Map<String, Object> first = demoBootstrapService.bootstrapDemoCompany();
        Map<String, Object> second = demoBootstrapService.bootstrapDemoCompany();

        assertThat(first.get("code")).isEqualTo("DEMO");
        assertThat(first.get("companyId")).isNotNull();
        assertThat(first.get("created")).isEqualTo(true);

        assertThat(second.get("code")).isEqualTo("DEMO");
        assertThat(second.get("companyId")).isEqualTo(first.get("companyId"));
        assertThat(second.get("created")).isEqualTo(false);
    }
}
