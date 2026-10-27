# Security

How authentication and related protections work, and why. Implemented in
Milestone 1; code in `backend/src/main/java/app/watchmark/auth` and `…/user`.

## Sessions

| Aspect | Decision |
|---|---|
| Mechanism | Server-side sessions via Spring Session JDBC (tables `SPRING_SESSION*`, created by Flyway V3). |
| Cookie | `WMSESSION`: `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age` 30 days, `Secure` when `SESSION_COOKIE_SECURE=true`. Defined in `SecurityConfig#cookieSerializer`. |
| Idle timeout | 14 days without use (`spring.session.timeout`, env `SESSION_TIMEOUT`). |
| Session id in index | The user **id** (not email) is the principal name, so sessions can be revoked per user. |
| Fixation | Login always issues a new session id. |
| Logout | `POST /api/v1/auth/logout` deletes the session row server-side. |
| Password change | Ends every *other* session of the user and rotates the current id. |
| Account deletion | Ends all sessions of the user. |

Why sessions rather than JWTs: for a browser SPA on one origin, an `HttpOnly`
cookie keeps the credential out of reach of JavaScript (XSS cannot steal it),
and logout/revocation take effect immediately. A future mobile app can get a
separate token-based filter chain without changing the controllers.

## CSRF

Double-submit cookie, Spring Security's SPA mode:

1. `GET /api/v1/auth/csrf` sets a readable `XSRF-TOKEN` cookie (`SameSite=Lax`).
2. The SPA sends it back in the `X-XSRF-TOKEN` header on every POST/PUT/PATCH/DELETE.
3. The token rotates on login and is cleared on logout; the client
   (`frontend/src/lib/api.ts`) fetches a fresh one when missing and retries once
   on `csrf-token-invalid`.

Requests without a matching header get `403` `/problems/csrf-token-invalid`.

## Passwords

- Hashed with bcrypt through `DelegatingPasswordEncoder` (hashes are stored as
  `{bcrypt}$2…`, so the algorithm can be upgraded later).
- Policy: **at least 15 characters, at most 72 bytes of UTF-8**. 15 follows
  NIST SP 800-63B-4 for passwords used as the only factor; no composition rules.
  72 bytes is bcrypt's input limit; Spring Security rejects longer input instead
  of silently truncating it (verified by `ValidationRulesTests`).
- Login applies no policy (so a future policy change never locks anyone out);
  over-long input is rejected as invalid credentials.
- Unknown email and wrong password produce byte-identical `401` responses, and
  Spring's `DaoAuthenticationProvider` equalises their timing.
- **Known limitation:** registration reports `409 email-taken`, which reveals
  that an address has an account. Hiding it requires email verification
  (future); until then registration is rate limited.
- Not yet implemented: breached-password checks, email verification, password
  reset, MFA.

## Rate limits

In-memory, fixed window, per application instance (`RateLimiter`).
Configured under `watchmark.rate-limit` in `application.yml`.

| Rule | Key | Default |
|---|---|---|
| Login per client | client IP | 30 / 5 min |
| Login per account | client IP + email | 5 / 5 min |
| Registration | client IP | 5 / hour |
| Password change, account deletion | user id | 10 / 15 min |

The per-account rule is keyed by IP **and** email so a remote attacker cannot
lock a victim out. Exceeding a limit returns `429` with `Retry-After`.

**Before deploying behind a reverse proxy:** the client IP comes from
`request.getRemoteAddr()`. Configure `server.forward-headers-strategy` and the
proxy's trusted `X-Forwarded-For` handling (Milestone 10), otherwise every
request appears to come from the proxy and shares one bucket.

## Authorization

- Public: `GET /actuator/health`, `GET /api/v1/auth/csrf`,
  `POST /api/v1/auth/register`, `POST /api/v1/auth/login`. Everything else
  requires a session (`401` otherwise).
- Personal endpoints never take a user id; they use the session principal.
  Tests prove one session cannot read or change another account.

## Errors and logging

- All errors are RFC 9457 `application/problem+json`, including those raised by
  security filters. No stack traces, exception messages or rejected values.
- Request DTOs holding passwords override `toString()` to redact them.

## Production checklist (Milestone 10)

- `SESSION_COOKIE_SECURE=true` (HTTPS only).
- Forwarded-header handling for real client IPs.
- HSTS and a Content-Security-Policy at the reverse proxy.
