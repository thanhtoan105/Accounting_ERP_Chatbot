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
@Schema(description = "Response DTO for schedule run history")
public class ScheduleRunDTO {

  @Schema(description = "Unique run identifier")
  private UUID id;

  @Schema(description = "Associated schedule ID")
  private UUID scheduleId;

  @Schema(description = "Schedule name")
  private String scheduleName;

  @Schema(description = "Accounting period ID for this run")
  private UUID periodId;

  @Schema(description = "Human-readable period label", example = "Q4 2024")
  private String periodLabel;

  @Schema(description = "Run status", example = "COMPLETED")
  private String status;

  @Schema(description = "Trigger type", example = "SCHEDULED")
  private String triggerType;

  @Schema(description = "User ID who triggered the run (for manual runs)")
  private Long triggeredById;

  @Schema(description = "User name who triggered the run")
  private String triggeredByName;

  @Schema(description = "Report snapshot ID")
  private UUID snapshotId;

  @Schema(description = "Original run ID if this is a rerun")
  private UUID rerunOfRunId;

  @Schema(description = "Attempt number for this run")
  private int attempt;

  @Schema(description = "Timestamp when run was queued")
  private Instant queuedAt;

  @Schema(description = "Timestamp when run started processing")
  private Instant startedAt;

  @Schema(description = "Timestamp when run finished")
  private Instant finishedAt;

  @Schema(description = "Run duration in milliseconds")
  private Long durationMs;

  @Schema(description = "Error code if run failed")
  private String errorCode;

  @Schema(description = "Error message if run failed")
  private String errorMessage;

  @Schema(description = "SLA deadline for this run")
  private Instant slaDeadline;

  @Schema(description = "Whether the run exceeded SLA")
  private boolean exceededSla;
}
