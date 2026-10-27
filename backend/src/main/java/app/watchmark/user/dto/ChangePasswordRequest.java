package app.watchmark.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import app.watchmark.user.validation.PasswordPolicy;

public record ChangePasswordRequest(@NotNull @Size(max = 1024) String currentPassword,
		@PasswordPolicy String newPassword) {

	@Override
	public String toString() {
		return "ChangePasswordRequest[***]";
	}

}
