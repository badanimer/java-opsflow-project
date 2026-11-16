package com.opsflow.ticket;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The five statuses of a ticket and the one table of the transitions between them (T-33; see Ticket
 * status and Status transitions in docs/database/database-design.md). The database checks only that a
 * status is one of the five; every change of status in the code asks {@link #canMoveTo} first, and a test
 * checks every pair, so a transition that is not in this table fails loudly. There is no way back from
 * {@code CLOSED} or {@code CANCELLED}, and a ticket is never reopened.
 */
public enum TicketStatus {

	OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED;

	/** The allowed transitions. Reassigning changes the assignee and not the status, so it is not here. */
	private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
			OPEN, EnumSet.of(IN_PROGRESS, CANCELLED),
			IN_PROGRESS, EnumSet.of(OPEN, RESOLVED, CANCELLED),
			RESOLVED, EnumSet.of(CLOSED),
			CLOSED, EnumSet.noneOf(TicketStatus.class),
			CANCELLED, EnumSet.noneOf(TicketStatus.class));

	public boolean canMoveTo(TicketStatus next) {
		return ALLOWED.get(this).contains(next);
	}

	/** Whether the ticket is frozen: its status, assignee, and links never change again. */
	public boolean isFinal() {
		return ALLOWED.get(this).isEmpty();
	}

	/** The status of a value in {@code tickets.status}. */
	public static TicketStatus of(String value) {
		return valueOf(value);
	}

}
