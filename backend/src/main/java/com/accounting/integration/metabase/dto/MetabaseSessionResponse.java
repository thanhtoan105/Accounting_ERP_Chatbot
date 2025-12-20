package com.accounting.integration.metabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetabaseSessionResponse(
        @JsonProperty("id") String sessionId) {
}
