package dev.kanakku.identity.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.regex.Pattern;

/** An optional UPI ID shaped like {@code handle@psp}. Empty is allowed; it is never verified. */
@Documented
@Constraint(validatedBy = ValidUpiId.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUpiId {
  String message() default "Use a UPI ID like name@okbank.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidUpiId, String> {
    private static final Pattern UPI = Pattern.compile("^[\\w.\\-]{2,256}@[a-zA-Z]{2,64}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || value.isBlank() || UPI.matcher(value.strip()).matches();
    }
  }
}
