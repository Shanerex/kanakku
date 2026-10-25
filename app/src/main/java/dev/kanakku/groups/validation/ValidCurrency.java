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
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An upper-case ISO 4217 code of a currency that has a minor unit. Codes such as XXX, XTS, XAU or
 * XDR have none (default fraction digits -1), so no amount in them can be held as minor units.
 */
@Documented
@Constraint(validatedBy = ValidCurrency.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCurrency {
  String message() default "Use a three-letter currency code such as INR.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<ValidCurrency, String> {
    private static final Set<String> CODES =
        Currency.getAvailableCurrencies().stream()
            .filter(c -> c.getDefaultFractionDigits() >= 0)
            .map(Currency::getCurrencyCode)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || CODES.contains(value);
    }
  }
}
