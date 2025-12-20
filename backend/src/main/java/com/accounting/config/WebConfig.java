package com.accounting.config;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * Web configuration for Spring Data pagination serialization.
 *
 * Using VIA_DTO mode ensures stable JSON structure for Page responses,
 * preventing breaking changes when upgrading Spring Data versions.
 *
 * This eliminates the warning:
 * "Serializing PageImpl instances as-is is not supported..."
 *
 * @see <a href="https://docs.spring.io/spring-data/commons/reference/repositories/core-extensions.html">Spring Data Core Extensions</a>
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
    // Empty body - annotation does all the work
}
