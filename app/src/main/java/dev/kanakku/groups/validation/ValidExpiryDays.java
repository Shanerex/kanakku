package dev.kanakku.groups.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Set;

/** An optional invite lifetime in days: 1, 7 or 30. */
@Documented
@Constraint(validatedBy = ValidExpiryDays.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidExpiryDays {
  String message() default "Choose 1, 7 or 30 days.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidExpiryDays, Integer> {
    private static final Set<Integer> ALLOWED = Set.of(1, 7, 30);

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
      return value == null || ALLOWED.contains(value);
    }
  }
}
