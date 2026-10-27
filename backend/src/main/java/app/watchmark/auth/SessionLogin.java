package app.watchmark.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import app.watchmark.user.validation.PasswordPolicy;

/** Authenticates credentials and binds the result to the HTTP session. */
@Component
class SessionLogin {

	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	private final AuthenticationManager authenticationManager;

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextRepository contextRepository;

	SessionLogin(AuthenticationManager authenticationManager, SessionAuthenticationStrategy loginSessionStrategy,
			SecurityContextRepository contextRepository) {
		this.authenticationManager = authenticationManager;
		this.sessionStrategy = loginSessionStrategy;
		this.contextRepository = contextRepository;
	}

	/**
	 * @throws BadCredentialsException for an unknown email or wrong password
	 */
	WatchmarkUserDetails login(String email, String password, HttpServletRequest request,
			HttpServletResponse response) {
		if (PasswordPolicy.Validator.exceedsMaxBytes(password)) {
			// Cannot match any stored bcrypt hash; avoid handing it to the encoder.
			throw new BadCredentialsException("Password too long");
		}
		Authentication authentication = authenticationManager
			.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));

		// New session id (prevents session fixation) and a fresh CSRF token.
		sessionStrategy.onAuthentication(authentication, request, response);

		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
		return (WatchmarkUserDetails) authentication.getPrincipal();
	}

}
