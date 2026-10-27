package app.watchmark.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(@NotNull @Size(max = 1024) String password) {

	@Override
	public String toString() {
		return "DeleteAccountRequest[***]";
	}

}
