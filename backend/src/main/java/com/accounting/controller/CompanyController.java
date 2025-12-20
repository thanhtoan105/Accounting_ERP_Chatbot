package com.accounting.controller;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.entity.Company;
import com.accounting.service.CompanyService;
import com.accounting.service.DemoBootstrapService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final DemoBootstrapService demoBootstrapService;

    public CompanyController(CompanyService companyService, DemoBootstrapService demoBootstrapService) {
        this.companyService = companyService;
        this.demoBootstrapService = demoBootstrapService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody Company input) {
        Company created = companyService.createCompany(input);
        Map<String, Object> body = new HashMap<>();
        body.put("data", created);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + created.getId())).body(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id, @Valid @RequestBody Company input) {
        Company updated = companyService.updateCompany(id, input);
        Map<String, Object> body = new HashMap<>();
        body.put("data", updated);
        return ResponseEntity.ok(body);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Company> data = companyService.listCompanies();
        Map<String, Object> body = new HashMap<>();
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/bootstrap-demo")
    public ResponseEntity<?> bootstrapDemo() {
        return ResponseEntity.ok(demoBootstrapService.bootstrapDemoCompany());
    }
}
