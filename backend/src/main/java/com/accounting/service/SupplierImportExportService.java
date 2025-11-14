package com.accounting.service;

import com.accounting.dto.SupplierDTO;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Supplier import/export operations.
 * Supports Excel (.xlsx, .xls) and CSV formats.
 */
public interface SupplierImportExportService {

  /**
   * Export suppliers to Excel or CSV format.
   *
   * @param suppliers list of suppliers to export
   * @param format export format ("xlsx", "xls", or "csv")
   * @param outputStream output stream to write the file
   */
  void exportSuppliers(List<SupplierDTO> suppliers, String format, OutputStream outputStream);

  /**
   * Import suppliers from Excel or CSV file.
   * Validates all rows before saving (atomic transaction).
   *
   * @param inputStream input stream of the file
   * @param filename original filename (used to detect format)
   * @return import result with success count, error count, and error details
   */
  ImportResult importSuppliers(InputStream inputStream, String filename);

  /**
   * Result of import operation.
   */
  class ImportResult {
    private int successCount;
    private int errorCount;
    private List<ImportError> errors;

    public ImportResult(int successCount, int errorCount, List<ImportError> errors) {
      this.successCount = successCount;
      this.errorCount = errorCount;
      this.errors = errors;
    }

    public int getSuccessCount() {
      return successCount;
    }

    public int getErrorCount() {
      return errorCount;
    }

    public List<ImportError> getErrors() {
      return errors;
    }
  }

  /**
   * Import error details for a specific row.
   */
  class ImportError {
    private int rowNumber;
    private String field;
    private String message;

    public ImportError(int rowNumber, String field, String message) {
      this.rowNumber = rowNumber;
      this.field = field;
      this.message = message;
    }

    public int getRowNumber() {
      return rowNumber;
    }

    public String getField() {
      return field;
    }

    public String getMessage() {
      return message;
    }
  }
}

