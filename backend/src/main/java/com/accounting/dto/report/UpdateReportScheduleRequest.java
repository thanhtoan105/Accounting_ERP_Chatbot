package com.accounting.dto.report;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request DTO for updating a report schedule (partial update supported)")
public class UpdateReportScheduleRequest {

  @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
  @Schema(description = "Schedule name", example = "Monthly Balance Sheet")
  private String name;

  @Pattern(
      regexp = "^(S06|B01|B02|B03|F01)$",
      message = "Report type must be one of: S06, B01, B02, B03, F01")
  @Schema(
      description = "Report type code",
      example = "B01",
      allowableValues = {"S06", "B01", "B02", "B03", "F01"})
  private String reportType;

  @Size(max = 100, message = "Cron expression must not exceed 100 characters")
  @Schema(description = "Cron expression for scheduling", example = "0 0 8 1 * ?")
  private String cronExpression;

  @Pattern(
      regexp = "^(CURRENT|PREVIOUS|SPECIFIC)$",
      message = "Period rule must be one of: CURRENT, PREVIOUS, SPECIFIC")
  @Schema(
      description = "Period rule for report generation",
      example = "PREVIOUS",
      allowableValues = {"CURRENT", "PREVIOUS", "SPECIFIC"})
  private String periodRule;

  @Schema(description = "Specific period ID (required when periodRule is SPECIFIC)")
  private UUID periodId;

  @Schema(description = "Export formats", example = "[\"PDF\", \"EXCEL\"]")
  private List<String> exportFormats;

  @Schema(description = "Recipient email addresses", example = "[\"user@example.com\"]")
  private List<String> recipients;

  @Schema(description = "Whether the schedule is active")
  private Boolean isActive;
}
