package com.accounting.integration.metabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetabaseGroupResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("member_count") Integer memberCount) {
}
