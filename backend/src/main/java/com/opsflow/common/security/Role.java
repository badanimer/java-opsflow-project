package com.opsflow.common.security;

import static com.opsflow.common.security.Permission.AUDIT_VIEW;
import static com.opsflow.common.security.Permission.DELETED_ITEMS_VIEW;
import static com.opsflow.common.security.Permission.ITEM_DELETE;
import static com.opsflow.common.security.Permission.ITEM_MANAGE;
import static com.opsflow.common.security.Permission.ORDER_PLACE;
import static com.opsflow.common.security.Permission.PRODUCT_DELIST;
import static com.opsflow.common.security.Permission.PRODUCT_LIST;
import static com.opsflow.common.security.Permission.PRODUCT_VIEW;
import static com.opsflow.common.security.Permission.ROLE_ASSIGN;
import static com.opsflow.common.security.Permission.ROLE_ASSIGN_PROTECTED;
import static com.opsflow.common.security.Permission.STOCK_CORRECT;
import static com.opsflow.common.security.Permission.STOCK_IN;
import static com.opsflow.common.security.Permission.STOCK_VIEW;
import static com.opsflow.common.security.Permission.STOCK_WRITE_OFF;
import static com.opsflow.common.security.Permission.TICKET_ARCHIVE;
import static com.opsflow.common.security.Permission.TICKET_ASSIGN;
import static com.opsflow.common.security.Permission.TICKET_CANCEL;
import static com.opsflow.common.security.Permission.TICKET_CREATE;
import static com.opsflow.common.security.Permission.TICKET_RELEASE;
import static com.opsflow.common.security.Permission.TICKET_RESOLVE;
import static com.opsflow.common.security.Permission.TICKET_SEARCH;
import static com.opsflow.common.security.Permission.TICKET_TAKE;
import static com.opsflow.common.security.Permission.TICKET_VIEW;
import static com.opsflow.common.security.Permission.TICKET_VIEW_ALL;
import static com.opsflow.common.security.Permission.USER_MANAGE;
import static com.opsflow.common.security.Permission.USER_MANAGE_PROTECTED;
import static com.opsflow.common.security.Permission.USER_VIEW;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The roles of OpsFlow, and what each of them may do: the columns of the Permission Matrix in
 * docs/database/database-design.md. This is the only place that ties a role to a permission.
 *
 * The roles are fixed: the program has no way to create, change, or delete a role (see Role management
 * in docs/database/database-design.md). The name of a constant is the {@code code} of its row in
 * {@code roles}, which the seed migration inserts; a test checks that the two agree.
 *
 * A new role is a backend change in two places: a migration that inserts the row, and a constant here
 * with its permissions. No endpoint changes, because an endpoint names a permission and not a role.
 */
public enum Role {

	ADMIN(
			PRODUCT_VIEW, PRODUCT_LIST, PRODUCT_DELIST,
			ITEM_MANAGE, ITEM_DELETE, STOCK_VIEW, DELETED_ITEMS_VIEW, STOCK_IN, STOCK_WRITE_OFF, STOCK_CORRECT,
			ORDER_PLACE,
			TICKET_VIEW, TICKET_VIEW_ALL, TICKET_SEARCH, TICKET_CREATE, TICKET_TAKE, TICKET_ASSIGN, TICKET_RELEASE,
			TICKET_RESOLVE, TICKET_CANCEL, TICKET_ARCHIVE,
			AUDIT_VIEW,
			USER_VIEW, USER_MANAGE, USER_MANAGE_PROTECTED, ROLE_ASSIGN, ROLE_ASSIGN_PROTECTED),

	MANAGER(
			PRODUCT_VIEW, PRODUCT_LIST, PRODUCT_DELIST,
			ITEM_MANAGE, ITEM_DELETE, STOCK_VIEW, DELETED_ITEMS_VIEW, STOCK_IN, STOCK_WRITE_OFF, STOCK_CORRECT,
			ORDER_PLACE,
			TICKET_VIEW, TICKET_VIEW_ALL, TICKET_SEARCH, TICKET_CREATE, TICKET_TAKE, TICKET_ASSIGN, TICKET_RELEASE,
			TICKET_RESOLVE, TICKET_CANCEL, TICKET_ARCHIVE,
			AUDIT_VIEW,
			USER_VIEW, USER_MANAGE, ROLE_ASSIGN),

	OPERATOR(
			PRODUCT_VIEW,
			STOCK_VIEW, DELETED_ITEMS_VIEW, STOCK_IN, STOCK_WRITE_OFF, STOCK_CORRECT,
			ORDER_PLACE,
			TICKET_VIEW, TICKET_CREATE, TICKET_TAKE, TICKET_RELEASE, TICKET_RESOLVE, TICKET_CANCEL),

	VIEWER(
			PRODUCT_VIEW,
			STOCK_VIEW, DELETED_ITEMS_VIEW,
			TICKET_VIEW, TICKET_VIEW_ALL, TICKET_SEARCH);

	private final Set<Permission> permissions;

	Role(Permission first, Permission... rest) {
		this.permissions = Collections.unmodifiableSet(EnumSet.of(first, rest));
	}

	/** The value of {@code roles.code}. */
	public String code() {
		return name();
	}

	/** What a user who holds this role may do. */
	public Set<Permission> permissions() {
		return permissions;
	}

	public boolean grants(Permission permission) {
		return permissions.contains(permission);
	}

	/**
	 * Whether the role is protected: only a user with {@link Permission#ROLE_ASSIGN_PROTECTED} may give
	 * it or take it away or change the roles of a user who holds it, and only a user with
	 * {@link Permission#USER_MANAGE_PROTECTED} may disable, enable, or soft-delete a user who holds it
	 * (see Authority over roles and accounts in
	 * docs/database/database-design.md). A new role is below manager, and so not protected, unless the
	 * change that adds it says otherwise here.
	 */
	public boolean isProtected() {
		return this == ADMIN || this == MANAGER;
	}

	/** The role with this {@code roles.code}, or empty for a code the program does not know. */
	public static Optional<Role> ofCode(String code) {
		for (Role role : values()) {
			if (role.code().equals(code)) {
				return Optional.of(role);
			}
		}
		return Optional.empty();
	}

}
