package dev.kanakku.groups.validation;

import dev.kanakku.groups.domain.CoverPreset;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An optional cover preset: {@code color:<name>} or {@code emoji:<one of the curated list>}. */
@Documented
@Constraint(validatedBy = ValidCover.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCover {
  String message() default "Pick one of the cover colours or emoji.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidCover, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || CoverPreset.normalize(value).isPresent();
    }
  }
}
