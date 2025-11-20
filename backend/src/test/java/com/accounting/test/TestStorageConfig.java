package com.accounting.test;

import com.accounting.service.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@TestConfiguration
public class TestStorageConfig {

  @Bean
  @Primary
  public StorageService storageServiceStub() {
    return new StorageService() {
      @Override
      public String uploadCompanyLogo(Long companyId, MultipartFile file) {
        return "https://example.test/storage/company-" + companyId + ".png";
      }

      @Override
      public String uploadVoucherAttachment(java.util.UUID voucherId, MultipartFile file) {
        String uuid = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        return "vouchers/" + voucherId + "/" + uuid + "-" + filename;
      }

      @Override
      public void deleteVoucherAttachment(String storagePath) {
        // Mock implementation - no-op
      }

      @Override
      public String uploadPurchaseBillAttachment(java.util.UUID purchaseBillId, MultipartFile file) {
        String uuid = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        return "purchase-bills/" + purchaseBillId + "/" + uuid + "-" + filename;
      }

      @Override
      public void deletePurchaseBillAttachment(String storagePath) {
        // Mock implementation - no-op
      }

      @Override
      public String generateSignedUrl(String storagePath, int expiresInSeconds) {
        return "https://example.test/storage/signed/" + storagePath + "?expires=" + expiresInSeconds;
      }
    };
  }
}


