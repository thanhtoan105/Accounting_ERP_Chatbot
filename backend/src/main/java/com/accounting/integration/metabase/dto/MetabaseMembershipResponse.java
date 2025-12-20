package com.accounting.integration.metabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetabaseMembershipResponse(
        @JsonProperty("membership_id") Long membershipId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("group_id") Long groupId) {
}
