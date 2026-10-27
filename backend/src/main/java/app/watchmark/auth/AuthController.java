package app.watchmark.auth;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.watchmark.auth.dto.LoginRequest;
import app.watchmark.user.EmailAddresses;
import app.watchmark.user.User;
import app.watchmark.user.UserService;
import app.watchmark.user.dto.RegisterRequest;
import app.watchmark.user.dto.UserResponse;

/** Logout is handled by Spring Security's LogoutFilter (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final UserService userService;

	private final SessionLogin sessionLogin;

	private final RateLimiter rateLimiter;

	private final RateLimitProperties limits;

	AuthController(UserService userService, SessionLogin sessionLogin, RateLimiter rateLimiter,
			RateLimitProperties limits) {
		this.userService = userService;
		this.sessionLogin = sessionLogin;
		this.rateLimiter = rateLimiter;
		this.limits = limits;
	}

	/**
	 * Makes sure the {@code XSRF-TOKEN} cookie is set. The SPA calls this before
	 * its first state-changing request and again after login or logout, which
	 * rotate the token.
	 */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token) {
		token.getToken(); // tokens are deferred; reading one writes the cookie
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/register")
	ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		rateLimiter.acquire("register-client:" + request.getRemoteAddr(), limits.registerPerClient());
		User user = userService.register(body);
		sessionLogin.login(body.email(), body.password(), request, response);
		return ResponseEntity.created(URI.create("/api/v1/me/profile")).body(UserResponse.from(user));
	}

	@PostMapping("/login")
	UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		String client = request.getRemoteAddr();
		rateLimiter.acquire("login-client:" + client, limits.loginPerClient());
		rateLimiter.acquire("login-account:" + client + ":" + EmailAddresses.normalize(body.email()),
				limits.loginPerAccount());
		WatchmarkUserDetails principal = sessionLogin.login(body.email(), body.password(), request, response);
		return UserResponse.from(userService.get(principal.id()));
	}

	@GetMapping("/me")
	UserResponse me(@AuthenticationPrincipal WatchmarkUserDetails principal) {
		return UserResponse.from(userService.get(principal.id()));
	}

}
