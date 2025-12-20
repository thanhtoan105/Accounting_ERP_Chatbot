package com.accounting.integration.metabase.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetabaseUserListResponse(
        @JsonProperty("data") List<MetabaseUserResponse> data,
        @JsonProperty("total") Integer total) {
}
