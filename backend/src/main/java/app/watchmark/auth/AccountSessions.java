package app.watchmark.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Revokes a user's server-side sessions after security-relevant account changes. */
@Component
public class AccountSessions {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	AccountSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	/**
	 * After a password change: sign out every other device and give the current
	 * session a new id.
	 */
	public void revokeOtherSessions(long userId, HttpServletRequest request) {
		HttpSession current = request.getSession(false);
		String currentId = current == null ? null : current.getId();
		sessions.findByPrincipalName(Long.toString(userId))
			.keySet()
			.stream()
			.filter(id -> !id.equals(currentId))
			.forEach(sessions::deleteById);
		if (current != null) {
			request.changeSessionId();
		}
	}

	/** After account deletion: end the current session and every other one. */
	public void revokeAllSessions(long userId, HttpServletRequest request, HttpServletResponse response) {
		new SecurityContextLogoutHandler().logout(request, response,
				SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication());
		sessions.findByPrincipalName(Long.toString(userId)).keySet().forEach(sessions::deleteById);
	}

}
