package com.accounting.service;

import java.util.List;

import com.accounting.dto.report.ReportDownloadLink;
import com.accounting.entity.report.ReportSchedule;
import com.accounting.entity.report.ReportScheduleRun;

public interface ReportDistributionService {

  ReportDownloadLink uploadAndGetSignedUrl(byte[] content, String filename, String format);

  void sendScheduledReportEmail(
      ReportSchedule schedule,
      ReportScheduleRun run,
      List<ReportDownloadLink> downloadLinks,
      byte[] pdfAttachment);

  void sendFailureNotification(ReportSchedule schedule, ReportScheduleRun run);
}
