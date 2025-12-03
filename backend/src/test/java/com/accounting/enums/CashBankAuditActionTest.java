package com.accounting.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link CashBankAuditAction} enum.
 * Verifies enum values, serialization, and helper methods work correctly.
 */
@DisplayName("CashBankAuditAction Enum")
class CashBankAuditActionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  @DisplayName("Enum Values")
  class EnumValuesTests {

    @Test
    @DisplayName("should have all required audit action types for Story 6.6")
    void shouldHaveAllRequiredActionTypes() {
      // Period Protection (AC6.6-01)
      assertThat(CashBankAuditAction.PERIOD_BLOCK_ATTEMPT).isNotNull();

      // Integrity Checks (AC6.6-03)
      assertThat(CashBankAuditAction.INTEGRITY_CHECK_PASS).isNotNull();
      assertThat(CashBankAuditAction.INTEGRITY_CHECK_FAIL).isNotNull();

      // Backup/Export (AC6.6-02)
      assertThat(CashBankAuditAction.BACKUP_EXPORT).isNotNull();

      // Audit Explorer (AC6.6-05)
      assertThat(CashBankAuditAction.AUDIT_EXPLORER_QUERY).isNotNull();

      // Anomaly Detection (AC6.6-03, AC6.6-06)
      assertThat(CashBankAuditAction.ANOMALY_DETECTED).isNotNull();

      // Purge Workflow (AC6.6-05)
      assertThat(CashBankAuditAction.PURGE_REQUEST).isNotNull();
      assertThat(CashBankAuditAction.PURGE_APPROVE).isNotNull();
      assertThat(CashBankAuditAction.PURGE_REJECT).isNotNull();

      // Alert Actions (AC6.6-06)
      assertThat(CashBankAuditAction.ALERT_SENT).isNotNull();

      // Export Controls (AC6.6-04)
      assertThat(CashBankAuditAction.COMPLIANCE_EXPORT).isNotNull();
    }

    @Test
    @DisplayName("should have exactly 11 audit action types")
    void shouldHaveCorrectNumberOfActionTypes() {
      assertThat(CashBankAuditAction.values()).hasSize(11);
    }
  }

  @Nested
  @DisplayName("Value Methods")
  class ValueMethodsTests {

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("each action should have a non-null, non-empty value")
    void eachActionShouldHaveValue(CashBankAuditAction action) {
      assertThat(action.getValue())
          .isNotNull()
          .isNotBlank()
          .matches("^[A-Z_]+$"); // Should be UPPER_SNAKE_CASE
    }

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("each action should have a non-null, non-empty display name")
    void eachActionShouldHaveDisplayName(CashBankAuditAction action) {
      assertThat(action.getDisplayName())
          .isNotNull()
          .isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("each action should have a non-null, non-empty description")
    void eachActionShouldHaveDescription(CashBankAuditAction action) {
      assertThat(action.getDescription())
          .isNotNull()
          .isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("toString should return the value")
    void toStringShouldReturnValue(CashBankAuditAction action) {
      assertThat(action.toString()).isEqualTo(action.getValue());
    }
  }

  @Nested
  @DisplayName("fromString Method")
  class FromStringTests {

    @Test
    @DisplayName("should return correct enum for valid value")
    void shouldReturnEnumForValidValue() {
      assertThat(CashBankAuditAction.fromString("PERIOD_BLOCK_ATTEMPT"))
          .isEqualTo(CashBankAuditAction.PERIOD_BLOCK_ATTEMPT);

      assertThat(CashBankAuditAction.fromString("INTEGRITY_CHECK_PASS"))
          .isEqualTo(CashBankAuditAction.INTEGRITY_CHECK_PASS);

      assertThat(CashBankAuditAction.fromString("PURGE_APPROVE"))
          .isEqualTo(CashBankAuditAction.PURGE_APPROVE);
    }

    @Test
    @DisplayName("should return null for invalid value")
    void shouldReturnNullForInvalidValue() {
      assertThat(CashBankAuditAction.fromString("INVALID_ACTION")).isNull();
      assertThat(CashBankAuditAction.fromString("")).isNull();
    }

    @Test
    @DisplayName("should return null for null input")
    void shouldReturnNullForNullInput() {
      assertThat(CashBankAuditAction.fromString(null)).isNull();
    }

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("fromString should work for all enum values")
    void fromStringShouldWorkForAllValues(CashBankAuditAction action) {
      assertThat(CashBankAuditAction.fromString(action.getValue())).isEqualTo(action);
    }
  }

  @Nested
  @DisplayName("isValid Method")
  class IsValidTests {

    @Test
    @DisplayName("should return true for valid values")
    void shouldReturnTrueForValidValues() {
      assertThat(CashBankAuditAction.isValid("PERIOD_BLOCK_ATTEMPT")).isTrue();
      assertThat(CashBankAuditAction.isValid("BACKUP_EXPORT")).isTrue();
      assertThat(CashBankAuditAction.isValid("ALERT_SENT")).isTrue();
    }

    @Test
    @DisplayName("should return false for invalid values")
    void shouldReturnFalseForInvalidValues() {
      assertThat(CashBankAuditAction.isValid("INVALID")).isFalse();
      assertThat(CashBankAuditAction.isValid("")).isFalse();
      assertThat(CashBankAuditAction.isValid(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("JSON Serialization")
  class JsonSerializationTests {

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("each action should serialize to JSON correctly")
    void eachActionShouldSerializeToJson(CashBankAuditAction action)
        throws JsonProcessingException {
      String json = objectMapper.writeValueAsString(action);

      // Jackson serializes enums as their name by default
      assertThat(json).isEqualTo("\"" + action.name() + "\"");
    }

    @ParameterizedTest
    @EnumSource(CashBankAuditAction.class)
    @DisplayName("each action should deserialize from JSON correctly")
    void eachActionShouldDeserializeFromJson(CashBankAuditAction action)
        throws JsonProcessingException {
      String json = "\"" + action.name() + "\"";
      CashBankAuditAction deserialized =
          objectMapper.readValue(json, CashBankAuditAction.class);

      assertThat(deserialized).isEqualTo(action);
    }

    @Test
    @DisplayName("should maintain serialization round-trip for all actions")
    void shouldMaintainSerializationRoundTrip() throws JsonProcessingException {
      for (CashBankAuditAction action : CashBankAuditAction.values()) {
        String json = objectMapper.writeValueAsString(action);
        CashBankAuditAction deserialized =
            objectMapper.readValue(json, CashBankAuditAction.class);

        assertThat(deserialized)
            .isEqualTo(action)
            .extracting(
                CashBankAuditAction::getValue,
                CashBankAuditAction::getDisplayName,
                CashBankAuditAction::getDescription)
            .containsExactly(
                action.getValue(),
                action.getDisplayName(),
                action.getDescription());
      }
    }
  }

  @Nested
  @DisplayName("Specific Action Value Verification")
  class SpecificValueVerificationTests {

    @Test
    @DisplayName("PERIOD_BLOCK_ATTEMPT should have correct value")
    void periodBlockAttemptShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.PERIOD_BLOCK_ATTEMPT.getValue())
          .isEqualTo("PERIOD_BLOCK_ATTEMPT");
    }

    @Test
    @DisplayName("INTEGRITY_CHECK_PASS should have correct value")
    void integrityCheckPassShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.INTEGRITY_CHECK_PASS.getValue())
          .isEqualTo("INTEGRITY_CHECK_PASS");
    }

    @Test
    @DisplayName("INTEGRITY_CHECK_FAIL should have correct value")
    void integrityCheckFailShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.INTEGRITY_CHECK_FAIL.getValue())
          .isEqualTo("INTEGRITY_CHECK_FAIL");
    }

    @Test
    @DisplayName("BACKUP_EXPORT should have correct value")
    void backupExportShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.BACKUP_EXPORT.getValue())
          .isEqualTo("BACKUP_EXPORT");
    }

    @Test
    @DisplayName("AUDIT_EXPLORER_QUERY should have correct value")
    void auditExplorerQueryShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.AUDIT_EXPLORER_QUERY.getValue())
          .isEqualTo("AUDIT_EXPLORER_QUERY");
    }

    @Test
    @DisplayName("ANOMALY_DETECTED should have correct value")
    void anomalyDetectedShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.ANOMALY_DETECTED.getValue())
          .isEqualTo("ANOMALY_DETECTED");
    }

    @Test
    @DisplayName("PURGE_REQUEST should have correct value")
    void purgeRequestShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.PURGE_REQUEST.getValue())
          .isEqualTo("PURGE_REQUEST");
    }

    @Test
    @DisplayName("PURGE_APPROVE should have correct value")
    void purgeApproveShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.PURGE_APPROVE.getValue())
          .isEqualTo("PURGE_APPROVE");
    }

    @Test
    @DisplayName("PURGE_REJECT should have correct value")
    void purgeRejectShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.PURGE_REJECT.getValue())
          .isEqualTo("PURGE_REJECT");
    }

    @Test
    @DisplayName("ALERT_SENT should have correct value")
    void alertSentShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.ALERT_SENT.getValue())
          .isEqualTo("ALERT_SENT");
    }

    @Test
    @DisplayName("COMPLIANCE_EXPORT should have correct value")
    void complianceExportShouldHaveCorrectValue() {
      assertThat(CashBankAuditAction.COMPLIANCE_EXPORT.getValue())
          .isEqualTo("COMPLIANCE_EXPORT");
    }
  }
}
