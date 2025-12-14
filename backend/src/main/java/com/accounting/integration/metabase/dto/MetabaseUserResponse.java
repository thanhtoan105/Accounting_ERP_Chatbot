package com.accounting.integration.metabase.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetabaseUserResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("email") String email,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("is_active") Boolean isActive,
        @JsonProperty("is_superuser") Boolean isSuperuser,
        @JsonProperty("group_ids") List<Long> groupIds) {
}
