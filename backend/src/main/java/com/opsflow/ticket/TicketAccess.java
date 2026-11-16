package com.opsflow.ticket;

import java.util.Collection;

import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.Role;

/**
 * Who sees which ticket, and who may hold one (see Ticket screens and who sees what and Who may take a
 * ticket in docs/database/database-design.md). The list, the detail, and every change ask the same
 * questions here, so an operator cannot reach by one path a ticket that another path hides.
 */
final class TicketAccess {

	private TicketAccess() {
	}

	/**
	 * Whether the user may see a ticket: every ticket with {@link Permission#TICKET_VIEW_ALL}; otherwise the
	 * pool, the tickets in progress assigned to them, the tickets they resolved, the cancelled tickets they
	 * created or last held, and no archived ticket.
	 */
	static boolean visible(CurrentUser user, TicketStatus status, long createdBy, Long assigneeId, Long resolvedBy) {
		if (user.can(Permission.TICKET_VIEW_ALL)) {
			return true;
		}
		long me = user.id();
		return switch (status) {
			case OPEN -> true;
			case IN_PROGRESS -> assigneeId != null && assigneeId == me;
			case RESOLVED -> resolvedBy != null && resolvedBy == me;
			case CANCELLED -> createdBy == me || (assigneeId != null && assigneeId == me);
			case CLOSED -> false;
		};
	}

	static boolean visible(CurrentUser user, TicketRepository.Locked ticket) {
		return visible(user, ticket.status(), ticket.createdBy(), ticket.assigneeId(), ticket.resolvedBy());
	}

	static boolean visible(CurrentUser user, TicketRepository.Related ticket) {
		return visible(user, ticket.status(), ticket.createdBy(), ticket.assigneeId(), ticket.resolvedBy());
	}

	/** Whether any of these roles grants the permission. */
	static boolean grants(Collection<String> roleCodes, Permission permission) {
		return roleCodes.stream().anyMatch(code -> Role.ofCode(code).filter(role -> role.grants(permission)).isPresent());
	}

}
