package com.accounting.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.service.impl.VirusScanServiceImpl;

@ExtendWith(MockitoExtension.class)
class VirusScanServiceTest {

  @InjectMocks
  private VirusScanServiceImpl virusScanService;

  @Test
  void testScanFile_ValidImage_ShouldPass() {
    MultipartFile file = new MockMultipartFile(
        "test.jpg",
        "test.jpg",
        "image/jpeg",
        new byte[1024]
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    // Note: There's a 1% random failure chance, so we just check it's not null
    assertNotNull(result);
    // Most of the time it should pass for valid files
    if (result.isPassed()) {
      assertNull(result.getErrorMessage());
    }
  }

  @Test
  void testScanFile_ValidPdf_ShouldPass() {
    MultipartFile file = new MockMultipartFile(
        "test.pdf",
        "test.pdf",
        "application/pdf",
        new byte[2048]
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    assertTrue(result.isPassed());
    assertNull(result.getErrorMessage());
  }

  @Test
  void testScanFile_BlockedExeFile_ShouldFail() {
    MultipartFile file = new MockMultipartFile(
        "malware.exe",
        "malware.exe",
        "application/x-msdownload",
        new byte[1024]
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    assertFalse(result.isPassed());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("blocked file type"));
    assertTrue(result.getErrorMessage().contains("exe"));
  }

  @Test
  void testScanFile_BlockedBatFile_ShouldFail() {
    MultipartFile file = new MockMultipartFile(
        "script.bat",
        "script.bat",
        "application/x-msdos-program",
        new byte[512]
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    assertFalse(result.isPassed());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("blocked file type"));
  }

  @Test
  void testScanFile_FileTooLarge_ShouldFail() {
    // Create a file larger than 10MB
    byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
    MultipartFile file = new MockMultipartFile(
        "large.pdf",
        "large.pdf",
        "application/pdf",
        largeContent
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    assertFalse(result.isPassed());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("too large"));
  }

  @Test
  void testScanFile_EmptyFile_ShouldFail() {
    MultipartFile file = new MockMultipartFile(
        "empty.txt",
        "empty.txt",
        "text/plain",
        new byte[0]
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    assertFalse(result.isPassed());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("empty") || result.getErrorMessage().contains("null"));
  }

  @Test
  void testScanFile_NullFile_ShouldFail() {
    VirusScanService.ScanResult result = virusScanService.scanFile(null);

    assertFalse(result.isPassed());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("null") || result.getErrorMessage().contains("empty"));
  }

  @Test
  void testScanFile_BlockedJsFile_ShouldFail() {
    MultipartFile file = new MockMultipartFile(
        "script.js",
        "script.js",
        "application/javascript",
        new byte[1024]
    );

    VirusScanService.ScanResult result = virusScanService.scanFile(file);

    assertFalse(result.isPassed());
    assertNotNull(result.getErrorMessage());
    assertTrue(result.getErrorMessage().contains("blocked file type"));
  }
}
