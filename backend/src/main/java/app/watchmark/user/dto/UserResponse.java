package app.watchmark.user.dto;

import app.watchmark.user.User;

/** The signed-in user's own account. Never includes the password hash. */
public record UserResponse(long id, String email, String displayName, String timeZone) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimeZone());
	}

}
