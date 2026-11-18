package com.opsflow.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * What the roles may do, as {@link Role} grants it (see Permission Matrix in
 * docs/database/database-design.md). The tests check the principles of the matrix and not each cell:
 * the cells are written once, in {@link Role}.
 */
class PermissionTest {

	/** A permission that no role grants would close its endpoints for everyone. */
	@Test
	void everyPermissionIsGrantedByARole() {
		Set<Permission> granted = EnumSet.noneOf(Permission.class);
		for (Role role : Role.values()) {
			granted.addAll(role.permissions());
		}

		assertThat(granted).containsExactlyInAnyOrder(Permission.values());
	}

	@Test
	void theRolesFollowThePrinciplesOfTheMatrix() {
		// MANAGER can do everything OPERATOR can, and ADMIN everything MANAGER can.
		assertThat(Role.MANAGER.permissions()).containsAll(Role.OPERATOR.permissions());
		assertThat(Role.ADMIN.permissions()).containsAll(Role.MANAGER.permissions());
		// VIEWER is read-only.
		assertThat(Role.VIEWER.permissions()).containsExactlyInAnyOrder(
				Permission.PRODUCT_VIEW, Permission.STOCK_VIEW, Permission.DELETED_ITEMS_VIEW, Permission.ORDER_VIEW_ALL,
				Permission.TICKET_VIEW, Permission.TICKET_VIEW_ALL, Permission.TICKET_SEARCH);
		// OPERATOR does not change master data, or see audit logs or the user page.
		assertThat(Role.OPERATOR.permissions()).doesNotContain(
				Permission.PRODUCT_LIST, Permission.PRODUCT_DELIST, Permission.ITEM_MANAGE, Permission.ITEM_DELETE,
				Permission.AUDIT_VIEW, Permission.USER_VIEW, Permission.USER_MANAGE, Permission.ROLE_ASSIGN);
		// The roles are not a ladder: a viewer sees everyone's tickets, and an operator only their own.
		assertThat(Role.OPERATOR.grants(Permission.TICKET_VIEW_ALL)).isFalse();
		// The same for orders: a viewer reads everyone's order records, an operator only their own.
		assertThat(Role.OPERATOR.grants(Permission.ORDER_VIEW_ALL)).isFalse();
		assertThat(Role.VIEWER.grants(Permission.ORDER_PLACE)).isFalse();
	}

	@Test
	void thePermissionsOfTheRolesOfAUserAddUp() {
		CurrentUser user = new CurrentUser(7, "olivia", List.of("OPERATOR", "VIEWER"));

		assertThat(user.can(Permission.ORDER_PLACE)).as("from OPERATOR").isTrue();
		assertThat(user.can(Permission.TICKET_VIEW_ALL)).as("from VIEWER").isTrue();
		assertThat(user.can(Permission.AUDIT_VIEW)).as("from neither").isFalse();

		Set<Permission> both = EnumSet.copyOf(Role.OPERATOR.permissions());
		both.addAll(Role.VIEWER.permissions());
		assertThat(user.permissions()).isEqualTo(both);
	}

	@Test
	void aUserWithNoRoleOrWithARoleTheProgramDoesNotKnowCanDoNothing() {
		CurrentUser noRole = new CurrentUser(7, "alice", List.of());
		CurrentUser unknownRole = new CurrentUser(8, "bob", List.of("AUDITOR"));

		for (Permission permission : Permission.values()) {
			assertThat(noRole.can(permission)).isFalse();
			assertThat(unknownRole.can(permission)).isFalse();
		}
		assertThat(noRole.permissions()).isEmpty();
		assertThat(unknownRole.permissions()).isEmpty();
	}

}
