package com.opsflow.common.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

	/**
	 * Whether the user may do this: whether any one of their roles grants the permission, because the
	 * roles of a user add up. This is the question to ask before an action; which role grants what is
	 * written in {@link Role}.
	 */
	public boolean can(Permission permission) {
		for (String code : roles) {
			if (Role.ofCode(code).filter(role -> role.grants(permission)).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Everything the user may do: the permissions of all their roles together, in the order of
	 * {@link Permission}. Empty for a user with no role.
	 */
	public Set<Permission> permissions() {
		Set<Permission> all = EnumSet.noneOf(Permission.class);
		for (String code : roles) {
			Role.ofCode(code).ifPresent(role -> all.addAll(role.permissions()));
		}
		return Collections.unmodifiableSet(all);
	}

	/**
	 * Whether the user holds this role now. For a rule that is about the role itself, such as who may
	 * change an administrator. Whether an action is allowed is asked with {@link #can}.
	 */
	public boolean holds(Role role) {
		return roles.contains(role.code());
	}

}
