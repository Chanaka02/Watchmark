package app.watchmark.user;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.common.ApiException;
import app.watchmark.user.dto.RegisterRequest;
import app.watchmark.user.dto.UpdateProfileRequest;
import app.watchmark.user.validation.PasswordPolicy;

@Service
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Creates an account. A taken email is reported as such: without email
	 * verification there is no way to hide it, so registration is rate limited
	 * instead (see AuthController).
	 */
	@Transactional
	public User register(RegisterRequest request) {
		String email = EmailAddresses.normalize(request.email());
		if (users.existsByEmail(email)) {
			throw emailTaken();
		}
		User user = new User(email, request.displayName().strip(), passwordEncoder.encode(request.password()),
				request.timeZone(), clock.instant());
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race with a concurrent registration for the same email.
			throw emailTaken();
		}
	}

	@Transactional(readOnly = true)
	public User get(long userId) {
		return users.findById(userId).orElseThrow(UserService::accountGone);
	}

	@Transactional
	public User updateProfile(long userId, UpdateProfileRequest request) {
		User user = get(userId);
		String displayName = request.displayName() == null ? null : request.displayName().strip();
		user.updateProfile(displayName, request.timeZone(), clock.instant());
		return user;
	}

	@Transactional
	public void changePassword(long userId, String currentPassword, String newPassword) {
		User user = get(userId);
		requirePassword(user, currentPassword);
		user.changePasswordHash(passwordEncoder.encode(newPassword), clock.instant());
	}

	/**
	 * Permanently deletes the account. Personal tables reference {@code users}
	 * with {@code ON DELETE CASCADE}, so all tracking data goes with it.
	 */
	@Transactional
	public void deleteAccount(long userId, String password) {
		User user = get(userId);
		requirePassword(user, password);
		users.delete(user);
	}

	private void requirePassword(User user, String password) {
		boolean matches = password != null && !PasswordPolicy.Validator.exceedsMaxBytes(password)
				&& passwordEncoder.matches(password, user.getPasswordHash());
		if (!matches) {
			// 403, not 401: the session is valid; only the confirmation failed.
			throw new ApiException(HttpStatus.FORBIDDEN, "password-incorrect", "Password incorrect",
					"The password you entered is incorrect.");
		}
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "email-taken", "Email already registered",
				"An account with this email already exists.");
	}

	private static ApiException accountGone() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication required",
				"Sign in to continue.");
	}

}
