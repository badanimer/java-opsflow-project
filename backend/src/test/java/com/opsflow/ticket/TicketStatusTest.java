package com.opsflow.ticket;

import static com.opsflow.ticket.TicketStatus.CANCELLED;
import static com.opsflow.ticket.TicketStatus.CLOSED;
import static com.opsflow.ticket.TicketStatus.IN_PROGRESS;
import static com.opsflow.ticket.TicketStatus.OPEN;
import static com.opsflow.ticket.TicketStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * T-33: every pair of the five statuses against the table of Status transitions in
 * docs/database/database-design.md. A pair that is not in the table must be refused.
 */
class TicketStatusTest {

	private record Pair(TicketStatus from, TicketStatus to) {
	}

	private static final Set<Pair> ALLOWED = Set.of(new Pair(OPEN, IN_PROGRESS), new Pair(OPEN, CANCELLED), new Pair(IN_PROGRESS, OPEN),
			new Pair(IN_PROGRESS, RESOLVED), new Pair(IN_PROGRESS, CANCELLED), new Pair(RESOLVED, CLOSED));

	@Test
	void everyPairOfStatusesIsAllowedExactlyWhenTheDesignAllowsIt() {
		for (TicketStatus from : TicketStatus.values()) {
			for (TicketStatus to : TicketStatus.values()) {
				assertThat(from.canMoveTo(to)).as("%s to %s", from, to).isEqualTo(ALLOWED.contains(new Pair(from, to)));
			}
		}
	}

	@Test
	void onlyClosedAndCancelledAreFinal() {
		for (TicketStatus status : TicketStatus.values()) {
			assertThat(status.isFinal()).as(status.name()).isEqualTo(status == CLOSED || status == CANCELLED);
		}
	}

}
