package com.accounting.service.impl.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class CronExpressionValidator {

  public void validate(String cronExpression) {
    try {
      CronExpression.parse(cronExpression);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid cron expression: " + cronExpression + " - " + e.getMessage());
    }
  }

  public Instant calculateNextRun(String cronExpression) {
    CronExpression cron = CronExpression.parse(cronExpression);
    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
    ZonedDateTime next = cron.next(now);
    return next != null ? next.toInstant() : null;
  }
}
