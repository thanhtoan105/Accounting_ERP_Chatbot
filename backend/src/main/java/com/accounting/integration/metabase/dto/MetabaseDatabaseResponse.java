package com.accounting.integration.metabase.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetabaseDatabaseResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("engine") String engine,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("is_sample") Boolean isSample) {
}
