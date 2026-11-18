package com.opsflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.opsflow.common.error.ApiException;
import com.opsflow.support.RaceRunner;
import com.opsflow.support.RaceRunner.Outcome;
import com.opsflow.user.UserService;
import com.opsflow.warehouse.ItemService;
import com.opsflow.warehouse.ProductService;

/**
 * The ticket races of Concurrency by Module (docs/database/database-design.md) against the real code: the
 * transactions, the locks, and the retry. Each race runs several rounds.
 */
class TicketRaceTest extends TicketIntegrationSupport {

	private static final int ROUNDS = 6;

	@Autowired
	UserService users;

	@Autowired
	ItemService items;

	@Autowired
	ProductService products;

	@Autowired
	TicketArchiveJob job;

	long adam;
	long mary;
	long olive;
	long otto;

	@BeforeEach
	void people() {
		adam = account("adam", "ADMIN");
		mary = account("mary", "MANAGER");
		olive = account("olive", "OPERATOR");
		otto = account("otto", "OPERATOR");
	}

	private interface Change {
		void run();
	}

	/** Runs the changes at the same moment and returns what each did: OK or the code of its refusal. */
	private static List<String> race(Change... changes) {
		AtomicInteger next = new AtomicInteger();
		String[] results = new String[changes.length];
		List<Outcome<Integer>> outcomes = RaceRunner.run(changes.length, () -> {
			int index = next.getAndIncrement();
			try {
				changes[index].run();
				results[index] = "OK";
			}
			catch (ApiException refusal) {
				results[index] = refusal.code().name();
			}
			return index;
		});
		assertThat(outcomes).as("no racer ended with an unexpected exception").allMatch(Outcome::succeeded);
		return List.of(results);
	}

	@Test
	void twoPeopleTakeTheSameTicketAndOneGetsIt() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = other(mary, "Race " + round);
			List<String> results = race(() -> tickets.take(as(olive), id, null), () -> tickets.take(as(otto), id, null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "TICKET_CHANGED");
			assertThat(history(id)).hasSize(1);
			assertThat(assigneeOf(id)).isIn(olive, otto);
		}
	}

	@Test
	void aPersonAt98InProgressTakesTwoTicketsAtOnceAndGetsOnlyOne() {
		for (int i = 0; i < 98; i++) {
			ticketRow("IN_PROGRESS", mary, olive, null);
		}
		for (int round = 0; round < ROUNDS; round++) {
			long first = other(mary, "First " + round);
			long second = other(mary, "Second " + round);
			List<String> results = race(() -> tickets.take(as(olive), first, null), () -> tickets.take(as(olive), second, null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "LIMIT_REACHED");
			assertThat(count("SELECT COUNT(*) FROM tickets WHERE assignee_id = ? AND status = 'IN_PROGRESS'", olive)).isEqualTo(99);
			// Make room again for the next round.
			root().update("UPDATE tickets SET status = 'OPEN', assignee_id = NULL WHERE id IN (?, ?)", first, second);
		}
	}

	@Test
	void aPersonAt98OpenOpensTwoTicketsAtOnceAndGetsOnlyOne() {
		for (int i = 0; i < 98; i++) {
			ticketRow("OPEN", olive, null, null);
		}
		for (int round = 0; round < ROUNDS; round++) {
			int before = count("SELECT COUNT(*) FROM tickets");
			List<String> results = race(() -> other(olive, "A"), () -> other(olive, "B"));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", "LIMIT_REACHED");
			assertThat(count("SELECT COUNT(*) FROM tickets WHERE created_by = ? AND status = 'OPEN'", olive)).isEqualTo(99);
			assertThat(count("SELECT COUNT(*) FROM tickets")).isEqualTo(before + 1);
			root().update("UPDATE tickets SET status = 'CANCELLED', cancelled_by = ?, cancel_reason = 'room', cancelled_at = NOW(3) WHERE id = (SELECT id FROM (SELECT MAX(id) AS id FROM tickets) t)",
					mary);
		}
	}

	@Test
	void aReassignmentAndAReleaseAtOnceNeverOverwriteEachOther() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = other(mary, "Contested " + round);
			tickets.take(as(olive), id, null);
			List<String> results = race(() -> tickets.reassign(as(mary), id, otto, olive, "move", null),
					() -> tickets.release(as(olive), id, olive, "give back", null));

			assertThat(results).as("round %d", round).containsExactlyInAnyOrder("OK", results.contains("NOT_FOUND") ? "NOT_FOUND" : "TICKET_CHANGED");
			assertThat(history(id)).as("the take and exactly one change").hasSize(2);
			if (results.get(0).equals("OK")) {
				assertThat(assigneeOf(id)).isEqualTo(otto);
			}
			else {
				assertThat(statusOf(id)).isEqualTo("OPEN");
				assertThat(assigneeOf(id)).isNull();
			}
		}
	}

	@Test
	void aUserIsDisabledWhileTakingATicketAndTheTicketEndsInThePool() {
		for (int round = 0; round < ROUNDS; round++) {
			long worker = account("worker" + round, "OPERATOR");
			long id = other(mary, "Taken while disabled " + round);
			List<String> results = race(() -> tickets.take(as(worker), id, null), () -> users.disable(as(adam), worker, null));

			assertThat(results.get(1)).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "ACCOUNT_DISABLED");
			assertThat(statusOf(id)).as("round %d", round).isEqualTo("OPEN");
			assertThat(assigneeOf(id)).isNull();
		}
	}

	@Test
	void aUserLosesTheRoleToTakeTicketsWhileTakingOneAndTheTicketEndsInThePool() {
		for (int round = 0; round < ROUNDS; round++) {
			long worker = account("taker" + round, "OPERATOR");
			long id = other(mary, "Taken while demoted " + round);
			List<String> results = race(() -> tickets.take(as(worker), id, null),
					() -> users.replaceRoles(as(adam), worker, List.of("VIEWER"), List.of("OPERATOR"), null));

			assertThat(results.get(1)).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "FORBIDDEN");
			assertThat(statusOf(id)).as("round %d", round).isEqualTo("OPEN");
			assertThat(assigneeOf(id)).isNull();
		}
	}

	@Test
	void aTicketOpenedOnAnItemAsItIsDeletedIsCancelledOrRefused() {
		for (int round = 0; round < ROUNDS; round++) {
			long item = item("Doomed " + round, mary);
			List<String> results = race(() -> onItems(olive, item), () -> items.delete(as(mary), item, null));

			assertThat(results.get(1)).isEqualTo("OK");
			assertThat(results.get(0)).as("round %d", round).isIn("OK", "ITEM_CHANGED");
			assertThat(count("""
					SELECT COUNT(*) FROM tickets t JOIN ticket_inventory ti ON ti.ticket_id = t.id
					WHERE ti.inventory_id = ? AND t.status IN ('OPEN', 'IN_PROGRESS')
					""", item)).as("no live ticket on a deleted item").isZero();
		}
	}

	@Test
	void twoBatchArchivesOfTheSameTicketsOverlapAndEachTicketIsArchivedOnce() {
		for (int round = 0; round < ROUNDS; round++) {
			long first = ticketRow("RESOLVED", mary, olive, olive);
			long second = ticketRow("RESOLVED", mary, olive, olive);
			List<String> results = race(() -> tickets.archiveBatch(as(mary), List.of(first, second), null),
					() -> tickets.archiveBatch(as(adam), List.of(second, first), null));

			assertThat(results).containsExactly("OK", "OK");
			assertThat(count("SELECT COUNT(*) FROM operation_logs WHERE action = 'TICKET_CLOSED' AND target_id IN (?, ?)", first, second))
					.as("round %d", round).isEqualTo(2);
		}
	}

	// T-34: the races that were left in What is not tested yet

	/** The reassignment wins and the old assignee may no longer resolve, or the resolution wins and the ticket is no longer in progress. */
	@Test
	void aReassignmentAndAResolutionAtOnceLetOnlyOneThrough() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = other(mary, "Resolved or moved " + round);
			tickets.take(as(olive), id, null);
			List<String> results = race(() -> tickets.reassign(as(mary), id, otto, olive, "move", null),
					() -> tickets.resolve(as(olive), id, "fixed", null));

			assertThat(results).as("round %d: %s", round, results).filteredOn("OK"::equals).hasSize(1);
			if (results.get(0).equals("OK")) {
				assertThat(statusOf(id)).isEqualTo("IN_PROGRESS");
				assertThat(assigneeOf(id)).isEqualTo(otto);
				assertThat(history(id)).as("the take and the reassignment").hasSize(2);
			}
			else {
				assertThat(statusOf(id)).isEqualTo("RESOLVED");
				assertThat(ticket(id)).containsEntry("resolved_by", olive);
				assertThat(history(id)).as("only the take").hasSize(1);
			}
		}
	}

	/** A manager may cancel a ticket in progress whoever holds it, so the cancellation always ends the ticket; a reassignment after it is refused. */
	@Test
	void aReassignmentAndACancellationAtOnceAlwaysEndCancelled() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = other(mary, "Cancelled or moved " + round);
			tickets.take(as(olive), id, null);
			List<String> results = race(() -> tickets.reassign(as(mary), id, otto, olive, "move", null),
					() -> tickets.cancel(as(adam), id, "not needed", null));

			assertThat(results.get(1)).as("round %d: %s", round, results).isEqualTo("OK");
			assertThat(results.get(0)).isIn("OK", "TICKET_CHANGED", "NOT_FOUND");
			assertThat(statusOf(id)).isEqualTo("CANCELLED");
			assertThat(history(id)).hasSize(results.get(0).equals("OK") ? 2 : 1);
			assertThat(count("SELECT COUNT(*) FROM operation_logs WHERE action = 'TICKET_CANCELLED' AND target_id = ?", id)).isEqualTo(1);
		}
	}

	@Test
	void theArchiveJobAndAManagerArchiveTheSameTicketOnce() {
		for (int round = 0; round < ROUNDS; round++) {
			long id = ticketRow("RESOLVED", mary, olive, olive);
			List<String> results = race(job::run, () -> tickets.archive(as(mary), id, null));

			assertThat(results.get(0)).isEqualTo("OK");
			assertThat(results.get(1)).as("round %d: %s", round, results).isIn("OK", "TICKET_CHANGED");
			assertThat(statusOf(id)).isEqualTo("CLOSED");
			assertThat(count("SELECT COUNT(*) FROM operation_logs WHERE action = 'TICKET_CLOSED' AND target_id = ?", id)).isEqualTo(1);
		}
	}

	/** The disabling returns the tickets to the pool, or the person's own release or resolution comes first; no ticket stays with a disabled user. */
	@Test
	void aUserIsDisabledWhileReleasingOrResolvingTheirOwnTicket() {
		for (int round = 0; round < ROUNDS; round++) {
			long worker = account("holder" + round, "OPERATOR");
			long released = other(mary, "Released while disabled " + round);
			long resolved = other(mary, "Resolved while disabled " + round);
			tickets.take(as(worker), released, null);
			tickets.take(as(worker), resolved, null);
			List<String> results = race(() -> users.disable(as(adam), worker, null),
					() -> tickets.release(as(worker), released, worker, "give back", null),
					() -> tickets.resolve(as(worker), resolved, "fixed", null));

			assertThat(results.get(0)).as("round %d: %s", round, results).isEqualTo("OK");
			assertThat(results.get(1)).isIn("OK", "ACCOUNT_DISABLED", "NOT_FOUND", "TICKET_CHANGED");
			assertThat(results.get(2)).isIn("OK", "ACCOUNT_DISABLED", "NOT_FOUND", "TICKET_CHANGED");
			assertThat(statusOf(released)).isEqualTo("OPEN");
			assertThat(assigneeOf(released)).isNull();
			assertThat(history(released)).as("the take and one return to the pool").hasSize(2);
			if (results.get(2).equals("OK")) {
				assertThat(statusOf(resolved)).isEqualTo("RESOLVED");
			}
			else {
				assertThat(statusOf(resolved)).isEqualTo("OPEN");
				assertThat(assigneeOf(resolved)).isNull();
			}
			assertThat(count("SELECT COUNT(*) FROM tickets WHERE assignee_id = ? AND status = 'IN_PROGRESS'", worker)).isZero();
		}
	}

	/**
	 * A ticket that exists when the delisting reads the links is cancelled by it; one committed after the
	 * delisting stays open. Neither side waits for a lock the other holds in the opposite order, so neither is
	 * refused as busy.
	 */
	@Test
	void aTicketOpenedOnAnItemAsItsProductIsDelistedIsCancelledOrOpenedAfter() {
		for (int round = 0; round < ROUNDS; round++) {
			long item = item("Delisted " + round, mary);
			products.list(as(mary), item, "Delisted " + round, "5.00", null);
			long product = root().queryForObject("SELECT id FROM products WHERE inventory_id = ?", Long.class, item);
			long[] ticket = new long[1];
			List<String> results = race(() -> ticket[0] = onItems(olive, item), () -> products.delist(as(mary), product, null));

			assertThat(results).as("round %d: %s", round, results).containsOnly("OK");
			String status = statusOf(ticket[0]);
			assertThat(status).isIn("OPEN", "CANCELLED");
			if (status.equals("CANCELLED")) {
				assertThat(ticket(ticket[0]).get("cancel_reason")).isEqualTo("PRODUCT_DELISTED:" + product);
			}
		}
	}

}
