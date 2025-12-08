package com.accounting.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service for virus scanning files.
 * Mock implementation that simulates virus scanning by checking file extensions and sizes.
 */
public interface VirusScanService {

  /**
   * Scan a file for viruses (mock implementation).
   * 
   * @param file file to scan
   * @return scan result
   */
  ScanResult scanFile(MultipartFile file);

  /**
   * Result of a virus scan.
   */
  class ScanResult {
    private final boolean passed;
    private final String errorMessage;

    public ScanResult(boolean passed, String errorMessage) {
      this.passed = passed;
      this.errorMessage = errorMessage;
    }

    public boolean isPassed() {
      return passed;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public static ScanResult pass() {
      return new ScanResult(true, null);
    }

    public static ScanResult fail(String errorMessage) {
      return new ScanResult(false, errorMessage);
    }
  }
}
