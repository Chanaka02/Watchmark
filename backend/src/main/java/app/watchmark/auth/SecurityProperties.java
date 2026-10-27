package app.watchmark.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param cookieSecure whether the session and XSRF-TOKEN cookies get the
 *     {@code Secure} attribute; must be true whenever the site is served over HTTPS
 * @param sessionCookieMaxAge how long the browser keeps the session cookie. The
 *     server-side idle timeout ({@code spring.session.timeout}) still applies, so
 *     an unused session expires sooner.
 */
@Validated
@ConfigurationProperties("watchmark.security")
public record SecurityProperties(boolean cookieSecure, @NotNull Duration sessionCookieMaxAge) {
}
