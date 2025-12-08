package com.accounting.dto.report;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for signed report download URLs")
public class ReportDownloadLink {

  @Schema(description = "Export format", example = "PDF")
  private String format;

  @Schema(description = "Signed download URL")
  private String url;

  @Schema(description = "URL expiration timestamp")
  private Instant expiresAt;

  @Schema(description = "SHA-256 hash of the file for integrity verification")
  private String hash;
}
