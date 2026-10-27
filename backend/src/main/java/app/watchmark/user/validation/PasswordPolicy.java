package app.watchmark.user.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Rules for a new password.
 * <ul>
 * <li>At least {@value #MIN_LENGTH} characters, following NIST SP 800-63B-4 for
 * passwords used as a single authentication factor. No composition rules: length
 * is what makes passwords strong, and passphrases are encouraged.</li>
 * <li>At most {@value #MAX_BYTES} bytes of UTF-8: bcrypt only uses the first 72
 * bytes, and Spring Security rejects longer input rather than truncating it.</li>
 * </ul>
 * {@code null} is invalid.
 */
@Documented
@Constraint(validatedBy = PasswordPolicy.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordPolicy {

	int MIN_LENGTH = 15;

	int MAX_BYTES = 72;

	String message() default "must be at least 15 characters and at most 72 bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<PasswordPolicy, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value != null && value.codePointCount(0, value.length()) >= MIN_LENGTH && !exceedsMaxBytes(value);
		}

		public static boolean exceedsMaxBytes(String value) {
			return value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
		}

	}

}
