package com.accounting.dto.report;

import java.time.Instant;
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
@Schema(description = "Response DTO for run-now endpoint")
public class RunNowResponse {

  @Schema(description = "Created run ID")
  private UUID runId;

  @Schema(description = "Schedule ID")
  private UUID scheduleId;

  @Schema(description = "Run status", example = "QUEUED")
  private String status;

  @Schema(description = "Period ID for this run")
  private UUID periodId;

  @Schema(description = "Human-readable period label", example = "Q4 2024")
  private String periodLabel;

  @Schema(description = "Timestamp when run was queued")
  private Instant queuedAt;
}
