package com.accounting.dto.report;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "Request DTO for creating a report schedule")
public class CreateReportScheduleRequest {

  @NotBlank(message = "Schedule name is required")
  @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
  @Schema(description = "Schedule name", example = "Monthly Balance Sheet", required = true)
  private String name;

  @NotBlank(message = "Report type is required")
  @Pattern(
      regexp = "^(S06|B01|B02|B03|F01)$",
      message = "Report type must be one of: S06, B01, B02, B03, F01")
  @Schema(
      description = "Report type code",
      example = "B01",
      allowableValues = {"S06", "B01", "B02", "B03", "F01"},
      required = true)
  private String reportType;

  @NotBlank(message = "Cron expression is required")
  @Size(max = 100, message = "Cron expression must not exceed 100 characters")
  @Schema(
      description = "Cron expression for scheduling",
      example = "0 0 8 1 * ?",
      required = true)
  private String cronExpression;

  @NotBlank(message = "Period rule is required")
  @Pattern(
      regexp = "^(LAST_CLOSED|CURRENT|SPECIFIC)$",
      message = "Period rule must be one of: LAST_CLOSED, CURRENT, SPECIFIC")
  @Schema(
      description = "Period rule for report generation",
      example = "LAST_CLOSED",
      allowableValues = {"LAST_CLOSED", "CURRENT", "SPECIFIC"},
      required = true)
  private String periodRule;

  @Schema(description = "Specific period ID (required when periodRule is SPECIFIC)")
  private UUID periodId;

  @NotEmpty(message = "At least one export format is required")
  @Schema(description = "Export formats", example = "[\"PDF\", \"EXCEL\"]", required = true)
  private List<@NotBlank(message = "Export format cannot be blank") String> exportFormats;

  @NotEmpty(message = "At least one recipient is required")
  @Schema(
      description = "Recipient email addresses",
      example = "[\"user@example.com\"]",
      required = true)
  private List<@NotBlank(message = "Recipient email cannot be blank") String> recipients;
}
