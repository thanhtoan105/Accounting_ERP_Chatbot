package com.accounting.validation;

import com.accounting.enums.Role;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for @ValidRole annotation. Validates that role string matches a valid Role enum.
 */
public class RoleValidator implements ConstraintValidator<ValidRole, String> {

  @Override
  public void initialize(ValidRole constraintAnnotation) {
    // No initialization needed
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      // Null values are handled by @NotNull if required
      return true;
    }
    return Role.isValid(value);
  }
}
