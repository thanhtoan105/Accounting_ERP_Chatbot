package com.accounting.service.impl.voucher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.accounting.dto.VoucherAttachmentDTO;
import com.accounting.entity.Attachment;
import com.accounting.entity.AttachmentEntityType;
import com.accounting.entity.User;
import com.accounting.entity.Voucher;
import com.accounting.repository.AttachmentRepository;
import com.accounting.repository.UserRepository;
import com.accounting.repository.VoucherRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.StorageService;
import com.accounting.service.VirusScanService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class VoucherAttachmentServiceImplTest {

  @Mock
  private AttachmentRepository attachmentRepository;

  @Mock
  private VoucherRepository voucherRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private StorageService storageService;

  @Mock
  private VirusScanService virusScanService;

  @InjectMocks
  private VoucherAttachmentServiceImpl attachmentService;

  private Long testCompanyId = 1L;
  private Long testUserId = 100L;
  private UUID testVoucherId;
  private Voucher testVoucher;
  private User testUser;

  @BeforeEach
  void setUp() {
    testVoucherId = UUID.randomUUID();
    CompanyContext.setCompanyId(testCompanyId);

    testVoucher = new Voucher();
    testVoucher.setId(testVoucherId);
    testVoucher.setCompanyId(testCompanyId);
    testVoucher.setStatus("draft");
    testVoucher.setEnteredBy(testUserId);

    testUser = new User();
    testUser.setId(testUserId);
    testUser.setFullName("Test User");
  }

  @Test
  void testUploadAttachment_Success() throws Exception {
    MultipartFile file =
        new MockMultipartFile("test.pdf", "test.pdf", "application/pdf", new byte[1024]);

    String storagePath = "vouchers/" + testVoucherId + "/uuid-test.pdf";

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));
    when(virusScanService.scanFile(file)).thenReturn(new VirusScanService.ScanResult(true, null));
    when(storageService.uploadVoucherAttachment(testVoucherId, file)).thenReturn(storagePath);

    Attachment savedAttachment = new Attachment();
    savedAttachment.setId(UUID.randomUUID());
    savedAttachment.setEntityType(AttachmentEntityType.VOUCHER);
    savedAttachment.setEntityId(testVoucherId);
    savedAttachment.setCompanyId(testCompanyId);
    savedAttachment.setFileName("test.pdf");
    savedAttachment.setStoragePath(storagePath);
    savedAttachment.setMimeType("application/pdf");
    savedAttachment.setFileSize(1024L);
    savedAttachment.setUploadedAt(Instant.now());
    savedAttachment.setUploadedBy(testUserId);

    when(attachmentRepository.save(any(Attachment.class))).thenReturn(savedAttachment);
    when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      VoucherAttachmentDTO result = attachmentService.uploadAttachment(testVoucherId, file);

      assertNotNull(result);
      assertEquals("test.pdf", result.getFileName());
      assertEquals("application/pdf", result.getMimeType());
      assertEquals(1024L, result.getFileSize());

      verify(virusScanService).scanFile(file);
      verify(storageService).uploadVoucherAttachment(testVoucherId, file);
      verify(attachmentRepository).save(any(Attachment.class));
    }
  }

  @Test
  void testUploadAttachment_VirusScanFails_ShouldThrowException() {
    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));

    MultipartFile validTypeFile =
        new MockMultipartFile("malware.pdf", "malware.pdf", "application/pdf", new byte[1024]);
    when(virusScanService.scanFile(validTypeFile))
        .thenReturn(new VirusScanService.ScanResult(false, "File failed virus scan: malware.pdf"));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class,
              () -> attachmentService.uploadAttachment(testVoucherId, validTypeFile));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertTrue(
          exception.getMessage().contains("virus scan")
              || exception.getMessage().contains("failed"));

      verify(virusScanService).scanFile(validTypeFile);
      verify(storageService, never()).uploadVoucherAttachment(any(), any());
      verify(attachmentRepository, never()).save(any());
    }
  }

  @Test
  void testUploadAttachment_InvalidFileType_ShouldThrowException() {
    MultipartFile file =
        new MockMultipartFile("test.exe", "test.exe", "application/x-msdownload", new byte[1024]);

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class,
              () -> attachmentService.uploadAttachment(testVoucherId, file));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("not supported"));

      verify(storageService, never()).uploadVoucherAttachment(any(), any());
      verify(attachmentRepository, never()).save(any());
    }
  }

  @Test
  void testUploadAttachment_FileTooLarge_ShouldThrowException() {
    byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
    MultipartFile file =
        new MockMultipartFile("large.pdf", "large.pdf", "application/pdf", largeContent);

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class,
              () -> attachmentService.uploadAttachment(testVoucherId, file));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("exceeds maximum"));

      verify(storageService, never()).uploadVoucherAttachment(any(), any());
      verify(attachmentRepository, never()).save(any());
    }
  }

  @Test
  void testListAttachments_Success() {
    Attachment attachment1 = new Attachment();
    attachment1.setId(UUID.randomUUID());
    attachment1.setEntityType(AttachmentEntityType.VOUCHER);
    attachment1.setEntityId(testVoucherId);
    attachment1.setCompanyId(testCompanyId);
    attachment1.setFileName("file1.pdf");
    attachment1.setMimeType("application/pdf");
    attachment1.setFileSize(1024L);
    attachment1.setUploadedAt(Instant.now());
    attachment1.setUploadedBy(testUserId);

    Attachment attachment2 = new Attachment();
    attachment2.setId(UUID.randomUUID());
    attachment2.setEntityType(AttachmentEntityType.VOUCHER);
    attachment2.setEntityId(testVoucherId);
    attachment2.setCompanyId(testCompanyId);
    attachment2.setFileName("file2.jpg");
    attachment2.setMimeType("image/jpeg");
    attachment2.setFileSize(2048L);
    attachment2.setUploadedAt(Instant.now());
    attachment2.setUploadedBy(testUserId);

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));
    when(attachmentRepository.findByVoucherIdAndCompanyId(testVoucherId, testCompanyId))
        .thenReturn(List.of(attachment1, attachment2));

    List<VoucherAttachmentDTO> result = attachmentService.listAttachments(testVoucherId);

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("file1.pdf", result.get(0).getFileName());
    assertEquals("file2.jpg", result.get(1).getFileName());
  }

  @Test
  void testGenerateSignedUrl_Success() {
    UUID attachmentId = UUID.randomUUID();
    Attachment attachment = new Attachment();
    attachment.setId(attachmentId);
    attachment.setEntityType(AttachmentEntityType.VOUCHER);
    attachment.setEntityId(testVoucherId);
    attachment.setCompanyId(testCompanyId);
    attachment.setStoragePath("vouchers/" + testVoucherId + "/uuid-file.pdf");

    String expectedSignedUrl = "https://storage.example.com/signed-url";

    when(attachmentRepository.findByVoucherIdAndIdAndCompanyId(
            testVoucherId, attachmentId, testCompanyId))
        .thenReturn(Optional.of(attachment));
    when(storageService.generateSignedUrl(attachment.getStoragePath(), 600))
        .thenReturn(expectedSignedUrl);

    String result = attachmentService.generateSignedUrl(testVoucherId, attachmentId);

    assertEquals(expectedSignedUrl, result);
    verify(storageService).generateSignedUrl(attachment.getStoragePath(), 600);
  }

  @Test
  void testDeleteAttachment_Success() {
    UUID attachmentId = UUID.randomUUID();
    Attachment attachment = new Attachment();
    attachment.setId(attachmentId);
    attachment.setEntityType(AttachmentEntityType.VOUCHER);
    attachment.setEntityId(testVoucherId);
    attachment.setCompanyId(testCompanyId);
    attachment.setStoragePath("vouchers/" + testVoucherId + "/uuid-file.pdf");
    attachment.setFileName("file.pdf");

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));
    when(attachmentRepository.findByVoucherIdAndIdAndCompanyId(
            testVoucherId, attachmentId, testCompanyId))
        .thenReturn(Optional.of(attachment));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      attachmentService.deleteAttachment(testVoucherId, attachmentId, "Test deletion reason");

      verify(storageService).deleteVoucherAttachment(attachment.getStoragePath());
      verify(attachmentRepository).delete(attachment);
    }
  }

  @Test
  void testDeleteAttachment_PostedVoucher_ShouldThrowException() {
    UUID attachmentId = UUID.randomUUID();
    testVoucher.setStatus("posted");

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class,
              () -> attachmentService.deleteAttachment(testVoucherId, attachmentId, "Reason"));

      assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("DRAFT status"));

      verify(storageService, never()).deleteVoucherAttachment(any());
      verify(attachmentRepository, never()).delete(any());
    }
  }

  @Test
  void testDeleteAttachment_MissingReason_ShouldThrowException() {
    UUID attachmentId = UUID.randomUUID();
    Attachment attachment = new Attachment();
    attachment.setId(attachmentId);
    attachment.setEntityType(AttachmentEntityType.VOUCHER);
    attachment.setEntityId(testVoucherId);
    attachment.setCompanyId(testCompanyId);

    when(voucherRepository.findByCompanyIdAndId(testCompanyId, testVoucherId))
        .thenReturn(Optional.of(testVoucher));
    when(attachmentRepository.findByVoucherIdAndIdAndCompanyId(
            testVoucherId, attachmentId, testCompanyId))
        .thenReturn(Optional.of(attachment));

    try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
      securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(testUserId);

      ResponseStatusException exception =
          assertThrows(
              ResponseStatusException.class,
              () -> attachmentService.deleteAttachment(testVoucherId, attachmentId, ""));

      assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
      assertTrue(exception.getMessage().contains("reason is required"));
    }
  }
}
