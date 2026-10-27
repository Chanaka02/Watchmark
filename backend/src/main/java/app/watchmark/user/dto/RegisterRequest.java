package app.watchmark.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import app.watchmark.user.validation.PasswordPolicy;
import app.watchmark.user.validation.TimeZoneId;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@PasswordPolicy String password,
		@NotBlank @Size(max = 50) String displayName,
		@NotBlank @TimeZoneId String timeZone) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", displayName=" + displayName + ", timeZone=" + timeZone + "]";
	}

}
