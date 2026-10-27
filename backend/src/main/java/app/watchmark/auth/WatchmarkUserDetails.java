package app.watchmark.auth;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal stored in the session.
 *
 * <p>
 * {@link #getUsername()} returns the user <em>id</em>, not the email. Spring
 * Session indexes sessions by this name (column {@code PRINCIPAL_NAME},
 * VARCHAR(100)), so using the id keeps the index short, stable if the email
 * ever changes, and lets us revoke all of a user's sessions reliably.
 * The password hash is erased after authentication and never serialised.
 */
public final class WatchmarkUserDetails implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

	private final long id;

	private transient String passwordHash;

	WatchmarkUserDetails(long id, String passwordHash) {
		this.id = id;
		this.passwordHash = passwordHash;
	}

	public long id() {
		return id;
	}

	@Override
	public String getUsername() {
		return Long.toString(id);
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return AUTHORITIES;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

	@Override
	public String toString() {
		return "WatchmarkUserDetails[id=" + id + "]";
	}

}
