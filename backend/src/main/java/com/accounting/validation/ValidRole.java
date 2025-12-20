package com.accounting.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that a role string value matches a valid Role enum value.
 */
@Documented
@Constraint(validatedBy = RoleValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRole {
  String message() default "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo, accountant_general, accountant_ar, accountant_ap, cashier, finance";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
