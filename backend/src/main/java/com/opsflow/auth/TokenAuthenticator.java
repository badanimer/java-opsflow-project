package com.opsflow.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.opsflow.auth.AuthRepository.Account;
import com.opsflow.common.security.CurrentUser;

/**
 * The check of the account on every request (T-20; see Token checks in docs/database/database-design.md).
 *
 * The signature and the expiry of the token are already checked when this runs. The token only names a
 * user, so the user and the roles are loaded from the database here, with plain reads. That is why
 * disabling an account, changing its roles, and resetting its password take effect on the next request.
 *
 * <ul>
 * <li>No such user, a soft-deleted account, or the SYSTEM identity: refused as unauthenticated.</li>
 * <li>A token that was not issued after {@code password_changed_at}, or that carries no session id (a token
 * issued before sessions were kept): refused as unauthenticated.</li>
 * <li>A disabled account: refused with {@link DisabledException}, which becomes {@code ACCOUNT_DISABLED}.</li>
 * <li>A token of an earlier login, whose session id is no longer {@code users.session_id} because the
 * account logged in again: refused with {@link SessionReplacedException}, which becomes
 * {@code SESSION_REPLACED}.</li>
 * </ul>
 */
@Component
public class TokenAuthenticator {

	/** The authentication of a request: the user as the database has it now. */
	static final class CurrentUserAuthentication extends AbstractAuthenticationToken {

		private static final long serialVersionUID = 1L;

		private final transient CurrentUser user;
		private final String sessionId;

		CurrentUserAuthentication(CurrentUser user, String sessionId) {
			super(user.roles().stream().map(code -> new SimpleGrantedAuthority("ROLE_" + code)).toList());
			this.user = user;
			this.sessionId = sessionId;
			setAuthenticated(true);
		}

		/** The id of the login the token of this request belongs to. */
		String sessionId() {
			return sessionId;
		}

		@Override
		public CurrentUser getPrincipal() {
			return user;
		}

		@Override
		public Object getCredentials() {
			return null;
		}

		@Override
		public String getName() {
			return user.username();
		}
	}

	/** The account logged in again after this token was issued: only the newest login is signed in. */
	static final class SessionReplacedException extends BadCredentialsException {

		private static final long serialVersionUID = 1L;

		SessionReplacedException() {
			super("The account signed in on another device");
		}
	}

	private final AuthRepository repository;

	TokenAuthenticator(AuthRepository repository) {
		this.repository = repository;
	}

	AbstractAuthenticationToken authenticate(Jwt jwt) {
		Long userId = TokenService.userId(jwt);
		Instant issuedAt = TokenService.issuedAt(jwt);
		if (userId == null || issuedAt == null) {
			throw new BadCredentialsException("The token names no user or no issue time");
		}
		Account account = repository.findById(userId).orElse(null);
		if (account == null || account.deleted() || account.system()) {
			throw new BadCredentialsException("The token belongs to no account");
		}
		if (account.passwordChangedAt() != null && !issuedAt.isAfter(account.passwordChangedAt())) {
			throw new BadCredentialsException("The token is older than the password");
		}
		String sessionId = TokenService.sessionId(jwt);
		if (sessionId == null) {
			throw new BadCredentialsException("The token belongs to no login");
		}
		if (account.disabled()) {
			throw new DisabledException("The account is disabled");
		}
		if (!sessionId.equals(account.sessionId())) {
			throw new SessionReplacedException();
		}
		List<String> roles = repository.roleCodes(account.id());
		return new CurrentUserAuthentication(new CurrentUser(account.id(), account.username(), roles), sessionId);
	}

}
