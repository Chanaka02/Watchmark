package app.watchmark.auth;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-cookie security for a same-origin SPA.
 *
 * <ul>
 * <li><b>Sessions</b> live in MySQL (Spring Session JDBC). The cookie is
 * HttpOnly and SameSite=Lax, and Secure in production.</li>
 * <li><b>CSRF</b>: double-submit cookie. The SPA reads the {@code XSRF-TOKEN}
 * cookie and echoes it in the {@code X-XSRF-TOKEN} header on every state-changing
 * request. This is Spring Security's built-in SPA mode.</li>
 * <li><b>Login</b> is a JSON endpoint (AuthController) instead of form login.
 * Because it authenticates outside a filter, it applies session fixation
 * protection and CSRF token rotation itself, through {@link #loginSessionStrategy}.</li>
 * <li><b>Errors</b> from the filter chain (401/403) go through the MVC exception
 * resolver, so they share the problem+json format of every other error.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	static final String SESSION_COOKIE_NAME = "WMSESSION";

	@Bean
	PasswordEncoder passwordEncoder() {
		// bcrypt today; the {id} prefix in stored hashes allows upgrading later.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		// DaoAuthenticationProvider also hashes against a dummy password for unknown
		// emails, so response time does not reveal whether an account exists.
		var provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(SecurityProperties properties) {
		// Readable by JavaScript by design: that is what makes double-submit work.
		var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.secure(properties.cookieSecure()).sameSite("Lax").path("/"));
		return repository;
	}

	/**
	 * The session cookie, defined explicitly rather than through
	 * {@code server.servlet.session.cookie.*}: Boot only applies those properties
	 * to Spring Session when running an embedded server, so tests could not verify
	 * them.
	 */
	@Bean
	CookieSerializer cookieSerializer(SecurityProperties properties) {
		var serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SESSION_COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(properties.cookieSecure());
		serializer.setCookieMaxAge(Math.toIntExact(properties.sessionCookieMaxAge().toSeconds()));
		return serializer;
	}

	@Bean
	SessionAuthenticationStrategy loginSessionStrategy(CookieCsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.requestCache(cache -> cache.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint(
						(request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex))
				.accessDeniedHandler(
						(request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex)))
			.authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
				.permitAll()
				.anyRequest()
				.authenticated());
		return http.build();
	}

}
