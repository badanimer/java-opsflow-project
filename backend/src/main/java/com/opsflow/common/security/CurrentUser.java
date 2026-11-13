package com.opsflow.common.security;

import java.util.List;

/**
 * The user who sent the request, as loaded from the database for this request (never from the token,
 * which carries only the id). The account is usable: an account that is disabled or soft-deleted never
 * gets this far.
 *
 * @param roles the codes of the roles the user holds now; empty for a user who waits for a role
 */
public record CurrentUser(long id, String username, List<String> roles) {

	public CurrentUser {
		roles = List.copyOf(roles);
	}

}
