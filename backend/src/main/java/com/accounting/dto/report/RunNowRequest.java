package com.accounting.dto.report;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for immediate report schedule run")
public class RunNowRequest {

  @Schema(description = "Override period ID (uses schedule's period rule if not provided)")
  private UUID overridePeriodId;

  @Schema(
      description = "Override export formats (uses schedule's formats if not provided)",
      example = "[\"PDF\"]")
  private List<String> overrideFormats;
}
