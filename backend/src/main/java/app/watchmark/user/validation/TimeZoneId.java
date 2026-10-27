package app.watchmark.user.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A region-based IANA time zone id such as {@code Asia/Colombo}. Fixed offsets
 * ({@code +05:30}) are rejected because they do not follow daylight-saving
 * changes. {@code null} is valid; combine with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = TimeZoneId.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeZoneId {

	String message() default "must be a valid IANA time zone, e.g. Europe/London";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<TimeZoneId, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || ZoneId.getAvailableZoneIds().contains(value);
		}

	}

}
