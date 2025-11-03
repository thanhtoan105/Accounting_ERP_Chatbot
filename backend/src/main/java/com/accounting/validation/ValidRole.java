package com.accounting.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a role string value matches a valid Role enum value.
 */
@Documented
@Constraint(validatedBy = RoleValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRole {
  String message() default "Invalid role. Must be one of: admin, accountant, chief_accountant, cfo";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}

