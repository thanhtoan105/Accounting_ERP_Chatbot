package com.accounting.dto.report;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@Schema(description = "Response DTO for report schedule list/detail")
public class ReportScheduleDTO {

  @Schema(description = "Unique schedule identifier")
  private UUID id;

  @Schema(description = "Schedule name", example = "Monthly Balance Sheet")
  private String name;

  @Schema(description = "Report type code", example = "B01")
  private String reportType;

  @Schema(description = "Cron expression for scheduling", example = "0 0 8 1 * ?")
  private String cronExpression;

  @Schema(description = "Period rule (CURRENT, PREVIOUS, SPECIFIC)", example = "PREVIOUS")
  private String periodRule;

  @Schema(description = "Additional report parameters")
  private Map<String, Object> parameters;

  @Schema(description = "Export formats", example = "[\"PDF\", \"EXCEL\"]")
  private List<String> exportFormats;

  @Schema(description = "Recipient email addresses")
  private List<String> recipients;

  @Schema(description = "Number of recipients")
  private int recipientCount;

  @Schema(description = "Owner user ID")
  private Long ownerId;

  @Schema(description = "Owner user name")
  private String ownerName;

  @Schema(description = "Whether the schedule is active")
  private boolean isActive;

  @Schema(description = "Timestamp of last run")
  private Instant lastRunAt;

  @Schema(description = "Timestamp of next scheduled run")
  private Instant nextRunAt;

  @Schema(description = "Schedule creation timestamp")
  private Instant createdAt;
}
