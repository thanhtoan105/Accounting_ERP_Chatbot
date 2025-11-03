package com.accounting.controller;

import com.accounting.entity.Customer;
import com.accounting.repository.CustomerRepository;
import com.accounting.repository.ScopedSpecifications;
import com.accounting.security.CompanyContext;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

  private final CustomerRepository customerRepository;

  public CustomerController(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@RequestBody Customer input) {
    Customer saved = customerRepository.save(input);
    Map<String, Object> body = new HashMap<>();
    body.put("data", saved);
    return ResponseEntity.created(URI.create("/api/v1/customers/" + saved.getId())).body(body);
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> list() {
    Long companyId = CompanyContext.getCompanyId();
    List<Customer> data;
    if (companyId != null) {
      data = customerRepository.findAll(ScopedSpecifications.companyScope());
    } else {
      data = customerRepository.findAll();
    }
    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    return ResponseEntity.ok(body);
  }
}


