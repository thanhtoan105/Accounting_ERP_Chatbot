package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.CustomerARSummaryDTO;
import com.accounting.dto.CustomerCreateRequest;
import com.accounting.dto.CustomerDTO;
import com.accounting.dto.CustomerUpdateRequest;
import com.accounting.entity.Customer;
import com.accounting.repository.CustomerRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.util.CustomerCodeGenerator;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

  @Mock private CustomerRepository customerRepository;

  @Mock private CustomerCodeGenerator codeGenerator;

  @Mock private AuditService auditService;

  private CustomerServiceImpl customerService;

  private static final Long TEST_COMPANY_ID = 1L;
  private static final Long TEST_CUSTOMER_ID = 100L;

  @BeforeEach
  void setUp() {
    customerService = new CustomerServiceImpl(customerRepository, codeGenerator, auditService);
    CompanyContext.setCompanyId(TEST_COMPANY_ID);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
  }

  @Test
  void findAll_withPagination_returnsPage() {
    Pageable pageable = PageRequest.of(0, 20);
    List<Customer> customers = List.of(createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Customer 1"));
    Page<Customer> customerPage = new PageImpl<>(customers, pageable, 1);

    when(customerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(customerPage);

    Page<CustomerDTO> result = customerService.findAll(pageable, null, null);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(1, result.getContent().size());
    assertEquals("Customer 1", result.getContent().get(0).getName());
  }

  @Test
  void findAll_withStatusFilter_filtersByStatus() {
    Pageable pageable = PageRequest.of(0, 20);
    Customer activeCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Active Customer");
    activeCustomer.setActive(true);
    List<Customer> customers = List.of(activeCustomer);
    Page<Customer> customerPage = new PageImpl<>(customers, pageable, 1);

    when(customerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(customerPage);

    Page<CustomerDTO> result = customerService.findAll(pageable, true, null);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertTrue(result.getContent().get(0).getActive());
  }

  @Test
  void findAll_withSearchTerm_usesNativeSearch() {
    Pageable pageable = PageRequest.of(0, 20);
    Customer matchingCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Matching Customer");
    List<Customer> customers = List.of(matchingCustomer);

    when(customerRepository.searchByCodeOrNameNative(TEST_COMPANY_ID, "Matching")).thenReturn(customers);

    Page<CustomerDTO> result = customerService.findAll(pageable, null, "Matching");

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals("Matching Customer", result.getContent().get(0).getName());
  }

  @Test
  void findAll_missingCompanyContext_throwsException() {
    CompanyContext.clear();
    Pageable pageable = PageRequest.of(0, 20);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.findAll(pageable, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Missing company context"));
  }

  @Test
  void getCustomerById_customerFound_returnsDTO() {
    Customer customer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Test Customer");
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(customer));

    Optional<CustomerDTO> result = customerService.getCustomerById(TEST_CUSTOMER_ID);

    assertTrue(result.isPresent());
    assertEquals("Test Customer", result.get().getName());
    assertEquals("CUST-2025-0001", result.get().getCode());
  }

  @Test
  void getCustomerById_customerNotFound_returnsEmpty() {
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.empty());

    Optional<CustomerDTO> result = customerService.getCustomerById(TEST_CUSTOMER_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void getCustomerById_wrongCompany_returnsEmpty() {
    Customer customer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Test Customer");
    customer.setCompanyId(999L); // Different company
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.empty());

    Optional<CustomerDTO> result = customerService.getCustomerById(TEST_CUSTOMER_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void create_withAutoGeneratedCode_generatesCode() {
    CustomerCreateRequest request = new CustomerCreateRequest();
    request.setName("New Customer");
    request.setActive(true);
    Customer savedCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "New Customer");
    
    when(codeGenerator.generateCode(TEST_COMPANY_ID)).thenReturn("CUST-2025-0001");
    lenient().when(customerRepository.findDuplicate(TEST_COMPANY_ID, null, null, null)).thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

    CustomerDTO result = customerService.create(request);

    assertNotNull(result);
    assertEquals("New Customer", result.getName());
    verify(codeGenerator).generateCode(TEST_COMPANY_ID);
    verify(auditService).logCustomerCreated(eq(TEST_CUSTOMER_ID), eq("CUST-2025-0001"), any(), any());
  }

  @Test
  void create_withProvidedCode_usesProvidedCode() {
    CustomerCreateRequest request = new CustomerCreateRequest();
    request.setCode("CUSTOM-001");
    request.setName("New Customer");
    request.setActive(true);
    Customer savedCustomer = createCustomer(TEST_CUSTOMER_ID, "CUSTOM-001", "New Customer");
    
    lenient().when(customerRepository.findDuplicate(TEST_COMPANY_ID, null, null, null)).thenReturn(Optional.empty());
    when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

    CustomerDTO result = customerService.create(request);

    assertNotNull(result);
    assertEquals("CUSTOM-001", result.getCode());
    verify(codeGenerator, never()).generateCode(anyLong());
  }

  @Test
  void create_withDuplicateTaxCode_throwsConflict() {
    CustomerCreateRequest request = new CustomerCreateRequest();
    request.setName("New Customer");
    request.setTaxCode("1234567890");
    request.setActive(true);
    Customer duplicate = createCustomer(999L, "CUST-2025-0001", "Existing Customer");
    duplicate.setTaxCode("1234567890");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
        .thenReturn(Optional.of(duplicate));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.create(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate customer found"));
    assertTrue(exception.getReason().contains("tax code"));
    verify(customerRepository, never()).save(any(Customer.class));
  }

  @Test
  void create_withDuplicateEmail_throwsConflict() {
    CustomerCreateRequest request = new CustomerCreateRequest();
    request.setName("New Customer");
    request.setEmail("duplicate@example.com");
    request.setActive(true);
    Customer duplicate = createCustomer(999L, "CUST-2025-0001", "Existing Customer");
    duplicate.setEmail("duplicate@example.com");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, null, "duplicate@example.com", null))
        .thenReturn(Optional.of(duplicate));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.create(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate customer found"));
    assertTrue(exception.getReason().contains("email"));
    verify(customerRepository, never()).save(any(Customer.class));
  }

  @Test
  void create_withDuplicatePhone_throwsConflict() {
    CustomerCreateRequest request = new CustomerCreateRequest();
    request.setName("New Customer");
    request.setPhone("+84123456789");
    request.setActive(true);
    Customer duplicate = createCustomer(999L, "CUST-2025-0001", "Existing Customer");
    duplicate.setPhone("+84123456789");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, null, null, "+84123456789"))
        .thenReturn(Optional.of(duplicate));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.create(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate customer found"));
    assertTrue(exception.getReason().contains("phone"));
    verify(customerRepository, never()).save(any(Customer.class));
  }

  @Test
  void update_updatesCustomer() {
    Customer existingCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Original Name");
    CustomerUpdateRequest request = new CustomerUpdateRequest();
    request.setName("Updated Name");
    Customer updatedCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Updated Name");
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(existingCustomer));
    when(customerRepository.save(any(Customer.class))).thenReturn(updatedCustomer);

    CustomerDTO result = customerService.update(TEST_CUSTOMER_ID, request);

    assertNotNull(result);
    assertEquals("Updated Name", result.getName());
    verify(auditService).logCustomerUpdated(eq(TEST_CUSTOMER_ID), eq("CUST-2025-0001"), any(), any(), any(), any());
  }

  @Test
  void update_customerNotFound_throwsNotFound() {
    CustomerUpdateRequest request = new CustomerUpdateRequest();
    request.setName("Updated Name");
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.update(TEST_CUSTOMER_ID, request));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Customer not found"));
  }

  @Test
  void update_withDuplicateTaxCode_throwsConflict() {
    Customer existingCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Customer 1");
    CustomerUpdateRequest request = new CustomerUpdateRequest();
    request.setTaxCode("1234567890");
    Customer duplicate = createCustomer(999L, "CUST-2025-0002", "Customer 2");
    duplicate.setTaxCode("1234567890");
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(existingCustomer));
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
        .thenReturn(Optional.of(duplicate));

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.update(TEST_CUSTOMER_ID, request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate customer found"));
  }

  @Test
  void delete_deletesCustomer() {
    Customer customer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "To Delete");
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(customer));

    customerService.delete(TEST_CUSTOMER_ID);

    verify(customerRepository).deleteById(TEST_CUSTOMER_ID);
    verify(auditService).logCustomerDeleted(eq(TEST_CUSTOMER_ID), eq("CUST-2025-0001"), anyString(), any(), any());
  }

  @Test
  void delete_customerNotFound_throwsNotFound() {
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.delete(TEST_CUSTOMER_ID));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Customer not found"));
    verify(customerRepository, never()).deleteById(anyLong());
  }

  @Test
  void activate_activatesCustomer() {
    Customer customer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Inactive Customer");
    customer.setActive(false);
    Customer activatedCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Inactive Customer");
    activatedCustomer.setActive(true);
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(customer));
    when(customerRepository.save(any(Customer.class))).thenReturn(activatedCustomer);

    customerService.activate(TEST_CUSTOMER_ID);

    verify(customerRepository).save(any(Customer.class));
    verify(auditService).logCustomerActivated(eq(TEST_CUSTOMER_ID), eq("CUST-2025-0001"), any(), any());
  }

  @Test
  void deactivate_deactivatesCustomer() {
    Customer customer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Active Customer");
    customer.setActive(true);
    Customer deactivatedCustomer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Active Customer");
    deactivatedCustomer.setActive(false);
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(customer));
    when(customerRepository.save(any(Customer.class))).thenReturn(deactivatedCustomer);

    customerService.deactivate(TEST_CUSTOMER_ID);

    verify(customerRepository).save(any(Customer.class));
    verify(auditService).logCustomerDeactivated(eq(TEST_CUSTOMER_ID), eq("CUST-2025-0001"), any(), any());
  }

  @Test
  void checkDuplicate_findsDuplicateByTaxCode() {
    Customer duplicate = createCustomer(999L, "CUST-2025-0001", "Duplicate Customer");
    duplicate.setTaxCode("1234567890");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
        .thenReturn(Optional.of(duplicate));

    Optional<Customer> result = customerService.checkDuplicate("1234567890", null, null, null);

    assertTrue(result.isPresent());
    assertEquals("1234567890", result.get().getTaxCode());
  }

  @Test
  void checkDuplicate_findsDuplicateByEmail() {
    Customer duplicate = createCustomer(999L, "CUST-2025-0001", "Duplicate Customer");
    duplicate.setEmail("duplicate@example.com");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, null, "duplicate@example.com", null))
        .thenReturn(Optional.of(duplicate));

    Optional<Customer> result = customerService.checkDuplicate(null, "duplicate@example.com", null, null);

    assertTrue(result.isPresent());
    assertEquals("duplicate@example.com", result.get().getEmail());
  }

  @Test
  void checkDuplicate_findsDuplicateByPhone() {
    Customer duplicate = createCustomer(999L, "CUST-2025-0001", "Duplicate Customer");
    duplicate.setPhone("+84123456789");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, null, null, "+84123456789"))
        .thenReturn(Optional.of(duplicate));

    Optional<Customer> result = customerService.checkDuplicate(null, null, "+84123456789", null);

    assertTrue(result.isPresent());
    assertEquals("+84123456789", result.get().getPhone());
  }

  @Test
  void checkDuplicate_excludesCurrentCustomer() {
    Customer duplicate = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Same Customer");
    duplicate.setTaxCode("1234567890");
    
    when(customerRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
        .thenReturn(Optional.of(duplicate));

    // Should return empty when excluding the same customer ID
    Optional<Customer> result = customerService.checkDuplicate("1234567890", null, null, TEST_CUSTOMER_ID);

    assertFalse(result.isPresent());
  }

  @Test
  void checkDuplicate_noFieldsProvided_returnsEmpty() {
    Optional<Customer> result = customerService.checkDuplicate(null, null, null, null);

    assertFalse(result.isPresent());
    verify(customerRepository, never()).findDuplicate(anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  void checkDuplicate_missingCompanyContext_returnsEmpty() {
    CompanyContext.clear();
    
    Optional<Customer> result = customerService.checkDuplicate("1234567890", null, null, null);

    assertFalse(result.isPresent());
    verify(customerRepository, never()).findDuplicate(anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  void getCustomerARSummary_returnsPlaceholder() {
    Customer customer = createCustomer(TEST_CUSTOMER_ID, "CUST-2025-0001", "Test Customer");
    
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.of(customer));

    CustomerARSummaryDTO result = customerService.getCustomerARSummary(TEST_CUSTOMER_ID);

    assertNotNull(result);
    assertEquals(0, result.getOpenInvoices());
    assertEquals(BigDecimal.ZERO, result.getTotalOwed());
    assertEquals(0, result.getAveragePaymentDays());
  }

  @Test
  void getCustomerARSummary_customerNotFound_throwsNotFound() {
    when(customerRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_CUSTOMER_ID))
        .thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> customerService.getCustomerARSummary(TEST_CUSTOMER_ID));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    assertTrue(exception.getReason() != null && exception.getReason().contains("Customer not found"));
  }

  // Helper method to create a customer
  private Customer createCustomer(Long id, String code, String name) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setCompanyId(TEST_COMPANY_ID);
    customer.setCode(code);
    customer.setName(name);
    customer.setActive(true);
    customer.setCreatedAt(Instant.now());
    customer.setUpdatedAt(Instant.now());
    return customer;
  }
}
