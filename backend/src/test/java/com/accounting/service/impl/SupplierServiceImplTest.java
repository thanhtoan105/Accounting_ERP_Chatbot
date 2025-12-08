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

import com.accounting.dto.SupplierAPSummaryDTO;
import com.accounting.dto.SupplierCreateRequest;
import com.accounting.dto.SupplierDTO;
import com.accounting.dto.SupplierUpdateRequest;
import com.accounting.entity.Supplier;
import com.accounting.repository.SupplierRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.util.SupplierCodeGenerator;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierCodeGenerator codeGenerator;

    @Mock
    private AuditService auditService;

    private SupplierServiceImpl supplierService;

    private static final Long TEST_COMPANY_ID = 1L;
    private static final Long TEST_SUPPLIER_ID = 100L;

    @BeforeEach
    void setUp() {
        supplierService = new SupplierServiceImpl(supplierRepository, codeGenerator, auditService);
        CompanyContext.setCompanyId(TEST_COMPANY_ID);
    }

    @AfterEach
    void tearDown() {
        CompanyContext.clear();
    }

    @Test
    void findAll_withPagination_returnsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        List<Supplier> suppliers = List.of(createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Supplier 1"));
        Page<Supplier> supplierPage = new PageImpl<>(suppliers, pageable, 1);

        when(supplierRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(supplierPage);

        Page<SupplierDTO> result = supplierService.findAll(pageable, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("Supplier 1", result.getContent().get(0).getName());
    }

    @Test
    void findAll_withStatusFilter_filtersByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Supplier activeSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Active Supplier");
        activeSupplier.setActive(true);
        List<Supplier> suppliers = List.of(activeSupplier);
        Page<Supplier> supplierPage = new PageImpl<>(suppliers, pageable, 1);

        when(supplierRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(supplierPage);

        Page<SupplierDTO> result = supplierService.findAll(pageable, true, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().get(0).getActive());
    }

    @Test
    void findAll_withSearchTerm_usesNativeSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        Supplier matchingSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Matching Supplier");
        List<Supplier> suppliers = List.of(matchingSupplier);

        when(supplierRepository.searchByCodeOrNameNative(TEST_COMPANY_ID, "Matching")).thenReturn(suppliers);

        Page<SupplierDTO> result = supplierService.findAll(pageable, null, "Matching");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Matching Supplier", result.getContent().get(0).getName());
    }

    @Test
    void findAll_missingCompanyContext_throwsException() {
        CompanyContext.clear();
        Pageable pageable = PageRequest.of(0, 20);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.findAll(pageable, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Missing company context"));
    }

    @Test
    void getSupplierById_supplierFound_returnsDTO() {
        Supplier supplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Test Supplier");
        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        Optional<SupplierDTO> result = supplierService.getSupplierById(TEST_SUPPLIER_ID);

        assertTrue(result.isPresent());
        assertEquals("Test Supplier", result.get().getName());
        assertEquals("SUP-2025-0001", result.get().getCode());
    }

    @Test
    void getSupplierById_supplierNotFound_returnsEmpty() {
        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.empty());

        Optional<SupplierDTO> result = supplierService.getSupplierById(TEST_SUPPLIER_ID);

        assertFalse(result.isPresent());
    }

    @Test
    void getSupplierById_wrongCompany_returnsEmpty() {
        Supplier supplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Test Supplier");
        supplier.setCompanyId(999L); // Different company
        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.empty());

        Optional<SupplierDTO> result = supplierService.getSupplierById(TEST_SUPPLIER_ID);

        assertFalse(result.isPresent());
    }

    @Test
    void create_withAutoGeneratedCode_generatesCode() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setName("New Supplier");
        request.setActive(true);
        Supplier savedSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "New Supplier");

        when(codeGenerator.generateCode(TEST_COMPANY_ID)).thenReturn("SUP-2025-0001");
        lenient().when(supplierRepository.findDuplicate(TEST_COMPANY_ID, null, null, null))
                .thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenReturn(savedSupplier);

        SupplierDTO result = supplierService.create(request);

        assertNotNull(result);
        assertEquals("New Supplier", result.getName());
        verify(codeGenerator).generateCode(TEST_COMPANY_ID);
        verify(auditService).logSupplierCreated(eq(TEST_SUPPLIER_ID), eq("SUP-2025-0001"), any(), any());
    }

    @Test
    void create_withProvidedCode_usesProvidedCode() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setCode("SUPPLIER-001");
        request.setName("New Supplier");
        request.setActive(true);
        Supplier savedSupplier = createSupplier(TEST_SUPPLIER_ID, "SUPPLIER-001", "New Supplier");

        lenient().when(supplierRepository.findDuplicate(TEST_COMPANY_ID, null, null, null))
                .thenReturn(Optional.empty());
        when(supplierRepository.save(any(Supplier.class))).thenReturn(savedSupplier);

        SupplierDTO result = supplierService.create(request);

        assertNotNull(result);
        assertEquals("SUPPLIER-001", result.getCode());
        verify(codeGenerator, never()).generateCode(anyLong());
    }

    @Test
    void create_withDuplicateTaxCode_throwsConflict() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setName("New Supplier");
        request.setTaxCode("1234567890");
        request.setActive(true);
        Supplier duplicate = createSupplier(999L, "SUP-2025-0001", "Existing Supplier");
        duplicate.setTaxCode("1234567890");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
                .thenReturn(Optional.of(duplicate));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.create(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate supplier found"));
        assertTrue(exception.getReason().contains("tax code"));
        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void create_withDuplicateEmail_throwsConflict() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setName("New Supplier");
        request.setEmail("duplicate@example.com");
        request.setActive(true);
        Supplier duplicate = createSupplier(999L, "SUP-2025-0001", "Existing Supplier");
        duplicate.setEmail("duplicate@example.com");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, null, "duplicate@example.com", null))
                .thenReturn(Optional.of(duplicate));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.create(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate supplier found"));
        assertTrue(exception.getReason().contains("email"));
        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void create_withDuplicatePhone_throwsConflict() {
        SupplierCreateRequest request = new SupplierCreateRequest();
        request.setName("New Supplier");
        request.setPhone("+84123456789");
        request.setActive(true);
        Supplier duplicate = createSupplier(999L, "SUP-2025-0001", "Existing Supplier");
        duplicate.setPhone("+84123456789");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, null, null, "+84123456789"))
                .thenReturn(Optional.of(duplicate));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.create(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate supplier found"));
        assertTrue(exception.getReason().contains("phone"));
        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    void update_updatesSupplier() {
        Supplier existingSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Original Name");
        SupplierUpdateRequest request = new SupplierUpdateRequest();
        request.setName("Updated Name");
        Supplier updatedSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Updated Name");

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.save(any(Supplier.class))).thenReturn(updatedSupplier);

        SupplierDTO result = supplierService.update(TEST_SUPPLIER_ID, request);

        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        verify(auditService).logSupplierUpdated(eq(TEST_SUPPLIER_ID), eq("SUP-2025-0001"), any(), any(), any(), any());
    }

    @Test
    void update_supplierNotFound_throwsNotFound() {
        SupplierUpdateRequest request = new SupplierUpdateRequest();
        request.setName("Updated Name");

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.update(TEST_SUPPLIER_ID, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Supplier not found"));
    }

    @Test
    void update_withDuplicateTaxCode_throwsConflict() {
        Supplier existingSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Supplier 1");
        SupplierUpdateRequest request = new SupplierUpdateRequest();
        request.setTaxCode("1234567890");
        Supplier duplicate = createSupplier(999L, "SUP-2025-0002", "Supplier 2");
        duplicate.setTaxCode("1234567890");

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
                .thenReturn(Optional.of(duplicate));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.update(TEST_SUPPLIER_ID, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Duplicate supplier found"));
    }

    @Test
    void delete_blocksDeletionWithReferentialIntegrity() {
        Supplier supplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "To Delete");

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        // Delete is blocked due to referential integrity check (Epic 4 not implemented)
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.delete(TEST_SUPPLIER_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Cannot delete supplier"));
        verify(supplierRepository, never()).deleteById(anyLong());
        verify(auditService).logSupplierDeleted(eq(TEST_SUPPLIER_ID), eq("SUP-2025-0001"), anyString(), any(), any());
    }

    @Test
    void delete_supplierNotFound_throwsNotFound() {
        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.delete(TEST_SUPPLIER_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Supplier not found"));
        verify(supplierRepository, never()).deleteById(anyLong());
    }

    @Test
    void activate_activatesSupplier() {
        Supplier supplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Inactive Supplier");
        supplier.setActive(false);
        Supplier activatedSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Inactive Supplier");
        activatedSupplier.setActive(true);

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenReturn(activatedSupplier);

        supplierService.activate(TEST_SUPPLIER_ID);

        verify(supplierRepository).save(any(Supplier.class));
        verify(auditService).logSupplierActivated(eq(TEST_SUPPLIER_ID), eq("SUP-2025-0001"), any(), any());
    }

    @Test
    void deactivate_deactivatesSupplier() {
        Supplier supplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Active Supplier");
        supplier.setActive(true);
        Supplier deactivatedSupplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Active Supplier");
        deactivatedSupplier.setActive(false);

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenReturn(deactivatedSupplier);

        supplierService.deactivate(TEST_SUPPLIER_ID);

        verify(supplierRepository).save(any(Supplier.class));
        verify(auditService).logSupplierDeactivated(eq(TEST_SUPPLIER_ID), eq("SUP-2025-0001"), any(), any());
    }

    @Test
    void checkDuplicate_findsDuplicateByTaxCode() {
        Supplier duplicate = createSupplier(999L, "SUP-2025-0001", "Duplicate Supplier");
        duplicate.setTaxCode("1234567890");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
                .thenReturn(Optional.of(duplicate));

        Optional<Supplier> result = supplierService.checkDuplicate("1234567890", null, null, null);

        assertTrue(result.isPresent());
        assertEquals("1234567890", result.get().getTaxCode());
    }

    @Test
    void checkDuplicate_findsDuplicateByEmail() {
        Supplier duplicate = createSupplier(999L, "SUP-2025-0001", "Duplicate Supplier");
        duplicate.setEmail("duplicate@example.com");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, null, "duplicate@example.com", null))
                .thenReturn(Optional.of(duplicate));

        Optional<Supplier> result = supplierService.checkDuplicate(null, "duplicate@example.com", null, null);

        assertTrue(result.isPresent());
        assertEquals("duplicate@example.com", result.get().getEmail());
    }

    @Test
    void checkDuplicate_findsDuplicateByPhone() {
        Supplier duplicate = createSupplier(999L, "SUP-2025-0001", "Duplicate Supplier");
        duplicate.setPhone("+84123456789");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, null, null, "+84123456789"))
                .thenReturn(Optional.of(duplicate));

        Optional<Supplier> result = supplierService.checkDuplicate(null, null, "+84123456789", null);

        assertTrue(result.isPresent());
        assertEquals("+84123456789", result.get().getPhone());
    }

    @Test
    void checkDuplicate_excludesCurrentSupplier() {
        Supplier duplicate = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Same Supplier");
        duplicate.setTaxCode("1234567890");

        when(supplierRepository.findDuplicate(TEST_COMPANY_ID, "1234567890", null, null))
                .thenReturn(Optional.of(duplicate));

        // Should return empty when excluding the same supplier ID
        Optional<Supplier> result = supplierService.checkDuplicate("1234567890", null, null, TEST_SUPPLIER_ID);

        assertFalse(result.isPresent());
    }

    @Test
    void checkDuplicate_noFieldsProvided_returnsEmpty() {
        Optional<Supplier> result = supplierService.checkDuplicate(null, null, null, null);

        assertFalse(result.isPresent());
        verify(supplierRepository, never()).findDuplicate(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void checkDuplicate_missingCompanyContext_returnsEmpty() {
        CompanyContext.clear();

        Optional<Supplier> result = supplierService.checkDuplicate("1234567890", null, null, null);

        assertFalse(result.isPresent());
        verify(supplierRepository, never()).findDuplicate(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void getSupplierAPSummary_returnsPlaceholder() {
        Supplier supplier = createSupplier(TEST_SUPPLIER_ID, "SUP-2025-0001", "Test Supplier");

        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.of(supplier));

        SupplierAPSummaryDTO result = supplierService.getSupplierAPSummary(TEST_SUPPLIER_ID);

        assertNotNull(result);
        assertEquals(0, result.getOpenBills());
        assertEquals(BigDecimal.ZERO, result.getTotalOwed());
        assertEquals(0, result.getAveragePaymentDays());
    }

    @Test
    void getSupplierAPSummary_supplierNotFound_throwsNotFound() {
        when(supplierRepository.findByCompanyIdAndId(TEST_COMPANY_ID, TEST_SUPPLIER_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> supplierService.getSupplierAPSummary(TEST_SUPPLIER_ID));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertTrue(exception.getReason() != null && exception.getReason().contains("Supplier not found"));
    }

    // Helper method to create a supplier
    private Supplier createSupplier(Long id, String code, String name) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setCompanyId(TEST_COMPANY_ID);
        supplier.setCode(code);
        supplier.setName(name);
        supplier.setActive(true);
        supplier.setCreatedAt(Instant.now());
        supplier.setUpdatedAt(Instant.now());
        return supplier;
    }
}
