package app.watchmark.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.AccountSessions;
import app.watchmark.auth.RateLimitProperties;
import app.watchmark.auth.RateLimiter;
import app.watchmark.auth.WatchmarkUserDetails;
import app.watchmark.user.dto.ChangePasswordRequest;
import app.watchmark.user.dto.DeleteAccountRequest;
import app.watchmark.user.dto.UpdateProfileRequest;
import app.watchmark.user.dto.UserResponse;

/** The signed-in user's own account. The user always comes from the session. */
@RestController
@RequestMapping("/api/v1/me")
class UserController {

	private final UserService userService;

	private final AccountSessions accountSessions;

	private final RateLimiter rateLimiter;

	private final RateLimitProperties limits;

	UserController(UserService userService, AccountSessions accountSessions, RateLimiter rateLimiter,
			RateLimitProperties limits) {
		this.userService = userService;
		this.accountSessions = accountSessions;
		this.rateLimiter = rateLimiter;
		this.limits = limits;
	}

	@GetMapping("/profile")
	UserResponse profile(@AuthenticationPrincipal WatchmarkUserDetails principal) {
		return UserResponse.from(userService.get(principal.id()));
	}

	@PatchMapping("/profile")
	UserResponse updateProfile(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@Valid @RequestBody UpdateProfileRequest body) {
		return UserResponse.from(userService.updateProfile(principal.id(), body));
	}

	@PutMapping("/password")
	ResponseEntity<Void> changePassword(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
		rateLimiter.acquire("sensitive-user:" + principal.id(), limits.sensitivePerUser());
		userService.changePassword(principal.id(), body.currentPassword(), body.newPassword());
		accountSessions.revokeOtherSessions(principal.id(), request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal WatchmarkUserDetails principal,
			@Valid @RequestBody DeleteAccountRequest body, HttpServletRequest request, HttpServletResponse response) {
		rateLimiter.acquire("sensitive-user:" + principal.id(), limits.sensitivePerUser());
		userService.deleteAccount(principal.id(), body.password());
		accountSessions.revokeAllSessions(principal.id(), request, response);
		return ResponseEntity.noContent().build();
	}

}
