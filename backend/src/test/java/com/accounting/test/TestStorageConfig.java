package com.accounting.test;

import com.accounting.service.StorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.multipart.MultipartFile;

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
    };
  }
}


