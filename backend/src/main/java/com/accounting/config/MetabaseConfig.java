package com.accounting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "metabase")
@Data
public class MetabaseConfig {

    private String siteUrl;
    private String embeddingSecret;
    private String apiKey;
    private int tokenExpiryMinutes = 10;
}
