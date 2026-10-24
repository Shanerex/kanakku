package dev.kanakku.identity.validation;

import dev.kanakku.identity.util.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || PasswordPolicy.isValid(value);
  }
}
