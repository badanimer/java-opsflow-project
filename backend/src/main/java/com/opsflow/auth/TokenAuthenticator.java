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
 * <li>A token that was not issued after {@code password_changed_at}: refused as unauthenticated.</li>
 * <li>A disabled account: refused with {@link DisabledException}, which becomes {@code ACCOUNT_DISABLED}.</li>
 * </ul>
 */
@Component
public class TokenAuthenticator {

	/** The authentication of a request: the user as the database has it now. */
	static final class CurrentUserAuthentication extends AbstractAuthenticationToken {

		private static final long serialVersionUID = 1L;

		private final transient CurrentUser user;

		CurrentUserAuthentication(CurrentUser user) {
			super(user.roles().stream().map(code -> new SimpleGrantedAuthority("ROLE_" + code)).toList());
			this.user = user;
			setAuthenticated(true);
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
		if (account.disabled()) {
			throw new DisabledException("The account is disabled");
		}
		List<String> roles = repository.roleCodes(account.id());
		return new CurrentUserAuthentication(new CurrentUser(account.id(), account.username(), roles));
	}

}
