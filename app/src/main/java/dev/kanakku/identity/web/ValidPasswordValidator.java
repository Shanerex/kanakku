package dev.kanakku.identity.web;

import dev.kanakku.identity.internal.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || PasswordPolicy.isValid(value);
  }
}
