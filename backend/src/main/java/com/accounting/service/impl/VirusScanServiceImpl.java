package com.accounting.service.impl;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.service.VirusScanService;

/**
 * Mock implementation of VirusScanService.
 * Simulates virus scanning by checking file extensions and sizes.
 * Blocks executable files and suspicious file types.
 */
@Service
public class VirusScanServiceImpl implements VirusScanService {

  // Blocked file extensions (executables and potentially dangerous files)
  private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
      "exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", "jar", "app", "deb", "rpm",
      "msi", "dmg", "sh", "ps1", "psm1", "psd1", "psc1", "cpl", "dll", "sys", "drv"
  );

  // Maximum file size for scan (10MB)
  private static final long MAX_SCAN_SIZE = 10 * 1024 * 1024;

  @Override
  public ScanResult scanFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return ScanResult.fail("File is empty or null");
    }

    // Check file extension
    String originalFilename = file.getOriginalFilename();
    if (originalFilename != null) {
      String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
      if (BLOCKED_EXTENSIONS.contains(extension)) {
        return ScanResult.fail("File failed virus scan: " + originalFilename + " (blocked file type: ." + extension + ")");
      }
    }

    // Check file size
    if (file.getSize() > MAX_SCAN_SIZE) {
      return ScanResult.fail("File failed virus scan: " + originalFilename + " (file too large: " + file.getSize() + " bytes)");
    }

    // Mock scan: simulate random failure (1% chance for demonstration)
    // In production, this would call an actual virus scanning service
    if (Math.random() < 0.01) {
      return ScanResult.fail("File failed virus scan: " + originalFilename + " (suspicious content detected)");
    }

    return ScanResult.pass();
  }

  private String getFileExtension(String filename) {
    int lastDot = filename.lastIndexOf('.');
    if (lastDot > 0 && lastDot < filename.length() - 1) {
      return filename.substring(lastDot + 1);
    }
    return "";
  }
}
