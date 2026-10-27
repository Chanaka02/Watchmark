package app.watchmark.user.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import app.watchmark.user.EmailAddresses;

class ValidationRulesTests {

	private final PasswordPolicy.Validator password = new PasswordPolicy.Validator();

	private final TimeZoneId.Validator timeZone = new TimeZoneId.Validator();

	@Test
	void passwordNeedsFifteenCharacters() {
		assertThat(password.isValid("a".repeat(14), null)).isFalse();
		assertThat(password.isValid("a".repeat(15), null)).isTrue();
		assertThat(password.isValid(null, null)).isFalse();
	}

	@Test
	void passwordLengthCountsCharactersNotUtf16Units() {
		// 14 emoji are 14 characters (28 UTF-16 units): still too short.
		assertThat(password.isValid("😀".repeat(14), null)).isFalse();
	}

	@Test
	void passwordIsCappedAt72Utf8Bytes() {
		assertThat(password.isValid("a".repeat(72), null)).isTrue();
		assertThat(password.isValid("a".repeat(73), null)).isFalse();
		// "é" is 2 bytes in UTF-8: 37 of them = 74 bytes.
		assertThat(password.isValid("é".repeat(37), null)).isFalse();
	}

	@Test
	void encoderRejectsPasswordsOver72BytesInsteadOfTruncating() {
		// Documents the library behaviour PasswordPolicy relies on.
		var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		assertThat(encoder.encode("a".repeat(72))).startsWith("{bcrypt}$2");
		assertThatThrownBy(() -> encoder.encode("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void timeZoneMustBeARegionId() {
		assertThat(timeZone.isValid("Asia/Colombo", null)).isTrue();
		assertThat(timeZone.isValid("America/Los_Angeles", null)).isTrue();
		assertThat(timeZone.isValid("+05:30", null)).isFalse();
		assertThat(timeZone.isValid("Mars/Olympus_Mons", null)).isFalse();
		assertThat(timeZone.isValid(null, null)).isTrue();
	}

	@Test
	void emailIsNormalisedIndependentlyOfLocale() {
		assertThat(EmailAddresses.normalize("  Ana.Silva@Example.COM ")).isEqualTo("ana.silva@example.com");
		assertThat(EmailAddresses.normalize("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
	}

}
