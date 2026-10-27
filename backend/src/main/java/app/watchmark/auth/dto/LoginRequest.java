package app.watchmark.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** No password policy here: login must keep working if the policy changes. */
public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotNull @Size(max = 1024) String password) {

	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
