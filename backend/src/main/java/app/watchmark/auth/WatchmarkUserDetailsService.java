package app.watchmark.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.watchmark.user.EmailAddresses;
import app.watchmark.user.UserRepository;

/** Looks users up by email at login time. */
@Service
class WatchmarkUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	WatchmarkUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) {
		return users.findByEmail(EmailAddresses.normalize(email))
			.map(user -> new WatchmarkUserDetails(user.getId(), user.getPasswordHash()))
			.orElseThrow(() -> new UsernameNotFoundException("No account for login"));
	}

}
