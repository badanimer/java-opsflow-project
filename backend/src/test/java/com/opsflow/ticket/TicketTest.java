package com.opsflow.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.time.UtcTime;

/**
 * The rules of the ticket module (T-14, T-33, T-35, T-36, T-40, T-44, T-47, T-21, T-39), each with a test
 * so that a missing check fails loudly. The races are in {@link TicketRaceTest}.
 */
class TicketTest extends TicketIntegrationSupport {

	@Autowired
	TicketArchiveJob job;

	long adam;
	long mary;
	long olive;
	long otto;
	long vera;

	@BeforeEach
	void people() {
		adam = account("adam", "ADMIN");
		mary = account("mary", "MANAGER");
		olive = account("olive", "OPERATOR");
		otto = account("otto", "OPERATOR");
		vera = account("vera", "VIEWER");
	}

	private static ErrorCode refusal(Runnable call) {
		ApiException refused = catchThrowableOfType(ApiException.class, call::run);
		assertThat(refused).as("the call is refused").isNotNull();
		return refused.code();
	}

	// Opening a ticket

	@Test
	void openingATicketOnItemsWritesItsNumberItsLinksAndItsLog() throws Exception {
		long paper = item("A4 paper", mary);
		long pens = item("Pens", mary);
		String json = "{\"type\":\"INVENTORY_EXCEPTION\",\"title\":\"  Count is off  \",\"description\":\"Shelf shows 3\",\"inventoryIds\":["
				+ pens + "," + paper + "]}";
		String answer = postAs("olive", "/tickets", json).andExpect(status().isCreated())
				.andExpect(jsonPath("$.ticketNo").value(matchesPattern("[A-Z0-9]{8}"))).andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.source").value("MANUAL")).andExpect(jsonPath("$.title").value("Count is off"))
				.andExpect(jsonPath("$.createdBy.username").value("olive")).andExpect(jsonPath("$.createdBy.system").value(false))
				.andExpect(jsonPath("$.items[*].name").value(contains("A4 paper", "Pens"))).andExpect(jsonPath("$.assignee").isEmpty())
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(answer, "$.id")).longValue();

		assertThat(count("SELECT COUNT(*) FROM ticket_inventory WHERE ticket_id = ?", id)).isEqualTo(2);
		List<Map<String, Object>> logs = logs(id);
		assertThat(logs).extracting(log -> log.get("action")).containsExactly("TICKET_CREATED");
		assertThat((String) logs.get(0).get("after_data")).contains("\"ticketNo\": \"" + numberOf(id) + "\"").contains("\"inventoryIds\": [" + paper);
		assertThat(logs.get(0).get("operator_id")).isEqualTo(olive);
	}

	@Test
	void theFieldsOfANewTicketAreCheckedBeforeAnythingIsWritten() {
		long paper = item("A4 paper", mary);
		List<Long> twentyOne = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			twentyOne.add(item("Item " + i, mary));
		}
		var olive = as(this.olive);

		assertThat(refusal(() -> tickets.create(olive, "NOTE", "t", "d", List.of(), null, null, null))).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.create(olive, "OTHER", "   ", "d", List.of(), null, null, null))).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.create(olive, "OTHER", "t", "x".repeat(4001), List.of(), null, null, null)))
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.create(olive, "INVENTORY_EXCEPTION", "t", "d", List.of(), null, null, null)))
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.create(olive, "ORDER_EXCEPTION", "t", "d", List.of(), " ", null, null)))
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.create(olive, "OTHER", "t", "d", twentyOne, null, null, null))).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.create(olive, "OTHER", "t", "d", List.of(paper, paper), null, null, null)))
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(count("SELECT COUNT(*) FROM tickets")).isZero();

		tickets.create(olive, "OTHER", "t", "x".repeat(4000), twentyOne.subList(0, 20), null, null, null);
		assertThat(count("SELECT COUNT(*) FROM tickets")).isEqualTo(1);
	}

	@Test
	void anOrderNumberMustBeTheNumberOfAnAttemptARefusedOneIncluded() {
		long paper = item("A4 paper", mary);
		root().update("INSERT INTO products (inventory_id, name, price, status, listed_at, listed_by, delisted_at, delisted_by) VALUES (?, 'A4 paper', 12.50, 'UNLISTED', NOW(3), ?, NOW(3), ?)",
				paper, mary, mary);
		long product = root().queryForObject("SELECT id FROM products WHERE inventory_id = ?", Long.class, paper);
		root().update("INSERT INTO order_attempts (order_no, user_id, product_id, product_name, quantity, result) VALUES ('ORD-abc123', ?, ?, 'A4 paper', 2, 'OUT_OF_STOCK')",
				olive, product);

		assertThat(refusal(() -> tickets.create(as(olive), "ORDER_EXCEPTION", "t", "d", List.of(), "ORD-nothing", null, null)))
				.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		long id = tickets.create(as(olive), "ORDER_EXCEPTION", "t", "d", List.of(), " ord-ABC123 ", null, null).ticket().id();
		assertThat(ticket(id).get("order_no")).isEqualTo("ORD-abc123");
	}

	@Test
	void onlyAResolvedOrArchivedTicketCanBeFollowed() throws Exception {
		long open = ticketRow("OPEN", mary, null, null);
		long resolved = ticketRow("RESOLVED", mary, otto, otto);
		long cancelled = ticketRow("CANCELLED", mary, null, null);

		assertThat(refusal(() -> tickets.create(as(olive), "OTHER", "t", "d", List.of(), null, "ZZZZZZZZ", null))).isEqualTo(ErrorCode.FOLLOW_NOT_FOUND);
		assertThat(refusal(() -> tickets.create(as(olive), "OTHER", "t", "d", List.of(), null, "short", null))).isEqualTo(ErrorCode.FOLLOW_NOT_FOUND);
		assertThat(refusal(() -> tickets.create(as(olive), "OTHER", "t", "d", List.of(), null, numberOf(open), null)))
				.isEqualTo(ErrorCode.FOLLOW_NOT_ALLOWED);
		assertThat(refusal(() -> tickets.create(as(olive), "OTHER", "t", "d", List.of(), null, numberOf(cancelled), null)))
				.isEqualTo(ErrorCode.FOLLOW_NOT_ALLOWED);

		long follow = tickets.create(as(olive), "OTHER", "t", "d", List.of(), null, numberOf(resolved).toLowerCase(), null).ticket().id();
		assertThat(ticket(follow).get("follows_ticket_id")).isEqualTo(resolved);

		// Olive cannot see the resolved ticket (otto resolved it): the link shows the number only.
		getAs("olive", "/tickets/" + follow).andExpect(jsonPath("$.follows.ticketNo").value(numberOf(resolved)))
				.andExpect(jsonPath("$.follows.id").isEmpty());
		getAs("mary", "/tickets/" + resolved).andExpect(jsonPath("$.followUps[0].id").value(follow));
	}

	@Test
	void aDeletedItemRefusesTheWholeTicketAndNamesTheItem() {
		long paper = item("A4 paper", mary);
		long pens = item("Pens", mary);
		root().update("UPDATE inventory SET deleted_at = NOW(3), deleted_by = ? WHERE id = ?", mary, pens);

		ApiException refused = catchThrowableOfType(ApiException.class,
				() -> tickets.create(as(olive), "INVENTORY_EXCEPTION", "t", "d", List.of(paper, pens, 999_999L), null, null, null));
		assertThat(refused.code()).isEqualTo(ErrorCode.ITEM_CHANGED);
		assertThat(refused.extras().get("items").toString()).contains("inventoryId=" + pens).contains("name=Pens").contains("inventoryId=999999");
		assertThat(count("SELECT COUNT(*) FROM tickets")).isZero();
		assertThat(count("SELECT COUNT(*) FROM ticket_inventory")).isZero();
		assertThat(count("SELECT COUNT(*) FROM operation_logs")).isZero();
	}

	@Test
	void aPersonWith99OpenTicketsCannotOpenAnotherUntilOneIsTaken() {
		List<Long> open = new ArrayList<>();
		for (int i = 0; i < 99; i++) {
			open.add(ticketRow("OPEN", olive, null, null));
		}
		assertThat(refusal(() -> other(olive, "one too many"))).isEqualTo(ErrorCode.LIMIT_REACHED);
		other(otto, "otto still can");

		tickets.take(as(mary), open.get(0), null);
		other(olive, "now there is room");
	}

	// Taking, assigning, reassigning

	@Test
	void takingMovesAnOpenTicketToInProgressAtOnceAndOnlyOnce() throws Exception {
		long id = other(mary, "Shelf broken");

		postAs("olive", "/tickets/" + id + "/take", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.assignee.username").value("olive")).andExpect(jsonPath("$.history[0].to.username").value("olive"));
		assertThat(history(id)).hasSize(1);
		assertThat(history(id).get(0)).containsEntry("to_user_id", olive).containsEntry("changed_by", olive).containsEntry("reason", "Taken from the pool");
		assertThat(history(id).get(0).get("from_user_id")).isNull();
		assertThat(logs(id).get(1).get("remark")).isEqualTo("taken");

		postAs("otto", "/tickets/" + id + "/take", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TICKET_CHANGED"))
				.andExpect(jsonPath("$.ticketStatus").doesNotExist());
		postAs("mary", "/tickets/" + id + "/take", null).andExpect(status().isConflict()).andExpect(jsonPath("$.ticketStatus").value("IN_PROGRESS"));
		postAs("vera", "/tickets/" + id + "/take", null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void aPersonWith99TicketsInProgressCannotTakeOrBeGivenAnother() {
		for (int i = 0; i < 99; i++) {
			ticketRow("IN_PROGRESS", mary, olive, null);
		}
		long id = other(mary, "One more");
		assertThat(refusal(() -> tickets.take(as(olive), id, null))).isEqualTo(ErrorCode.LIMIT_REACHED);
		assertThat(refusal(() -> tickets.assign(as(mary), id, olive, "you know it", null))).isEqualTo(ErrorCode.LIMIT_REACHED);

		long held = ticketRow("IN_PROGRESS", mary, otto, null);
		assertThat(refusal(() -> tickets.reassign(as(mary), held, olive, otto, "move", null))).isEqualTo(ErrorCode.LIMIT_REACHED);
		assertThat(statusOf(id)).isEqualTo("OPEN");
	}

	@Test
	void anAssignmentGoesOnlyToAnEligibleAssigneeAndNeedsAReason() {
		long disabled = account("dora", "OPERATOR");
		root().update("UPDATE users SET status = 'DISABLED' WHERE id = ?", disabled);
		long noRole = account("nora");
		long id = other(otto, "Needs hands");

		assertThat(refusal(() -> tickets.assign(as(mary), id, vera, "r", null))).isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.assign(as(mary), id, disabled, "r", null))).isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.assign(as(mary), id, noRole, "r", null))).isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.assign(as(mary), id, systemIdentity(), "r", null))).isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.assign(as(mary), id, 999_999L, "r", null))).isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.assign(as(mary), id, adam, "r", null))).as("a manager never gives a ticket to an administrator")
				.isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.assign(as(mary), id, olive, "  ", null))).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(statusOf(id)).isEqualTo("OPEN");

		tickets.assign(as(adam), id, adam, "I will", null);
		assertThat(assigneeOf(id)).isEqualTo(adam);
		assertThat(history(id).get(0).get("reason")).isEqualTo("I will");
		assertThat(logs(id).get(1).get("remark")).isEqualTo("assigned");
	}

	@Test
	void aReassignmentNeedsTheAssigneeTheActorSawAndTakesTheTicketFromThePreviousAssignee() {
		long id = other(mary, "Hand over");
		tickets.take(as(olive), id, null);

		assertThat(refusal(() -> tickets.reassign(as(mary), id, otto, mary, "r", null))).isEqualTo(ErrorCode.TICKET_CHANGED);
		assertThat(refusal(() -> tickets.reassign(as(mary), id, olive, olive, "r", null))).isEqualTo(ErrorCode.NOT_ELIGIBLE);
		assertThat(refusal(() -> tickets.reassign(as(mary), id, otto, olive, "", null))).isEqualTo(ErrorCode.VALIDATION_FAILED);

		tickets.reassign(as(mary), id, otto, olive, "olive is away", null);
		assertThat(assigneeOf(id)).isEqualTo(otto);
		assertThat(history(id).get(1)).containsEntry("from_user_id", olive).containsEntry("to_user_id", otto).containsEntry("changed_by", mary);
		assertThat(logs(id).get(2)).containsEntry("action", "TICKET_REASSIGNED").containsEntry("remark", "olive is away");

		// Olive no longer holds it and no longer sees it.
		assertThat(refusal(() -> tickets.resolve(as(olive), id, "done", null))).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(refusal(() -> tickets.release(as(olive), id, olive, "r", null))).isEqualTo(ErrorCode.NOT_FOUND);
	}

	// Releasing, resolving, cancelling, archiving

	@Test
	void aReleaseReturnsTheTicketToThePoolWithAReason() {
		long id = other(mary, "Back to pool");
		tickets.take(as(olive), id, null);

		assertThat(refusal(() -> tickets.release(as(olive), id, olive, " ", null))).isEqualTo(ErrorCode.VALIDATION_FAILED);
		assertThat(refusal(() -> tickets.release(as(mary), id, otto, "r", null))).as("the assignee the manager saw is not the one now")
				.isEqualTo(ErrorCode.TICKET_CHANGED);

		tickets.release(as(olive), id, olive, "not my area", null);
		assertThat(statusOf(id)).isEqualTo("OPEN");
		assertThat(assigneeOf(id)).isNull();
		assertThat(history(id).get(1)).containsEntry("from_user_id", olive).containsEntry("to_user_id", null).containsEntry("reason", "not my area");
		assertThat(logs(id).get(2)).containsEntry("action", "TICKET_RELEASED").containsEntry("operator_id", olive);

		tickets.take(as(otto), id, null);
		tickets.release(as(mary), id, otto, "otto is needed elsewhere", null);
		assertThat(statusOf(id)).isEqualTo("OPEN");
	}

	@Test
	void anOperatorResolvesOnlyTheirOwnTicketAndAManagerAny() {
		long mine = other(mary, "Mine");
		long hers = other(mary, "Hers");
		tickets.take(as(olive), mine, null);
		tickets.take(as(otto), hers, null);

		assertThat(refusal(() -> tickets.resolve(as(olive), hers, "done", null))).as("hidden from olive").isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(refusal(() -> tickets.resolve(as(olive), mine, "x".repeat(2001), null))).isEqualTo(ErrorCode.VALIDATION_FAILED);

		tickets.resolve(as(olive), mine, "  fixed the shelf  ", null);
		Map<String, Object> row = ticket(mine);
		assertThat(row).containsEntry("status", "RESOLVED").containsEntry("resolution", "fixed the shelf").containsEntry("resolved_by", olive)
				.containsEntry("assignee_id", olive);
		assertThat(row.get("resolved_at")).isNotNull();

		tickets.resolve(as(mary), hers, "done for otto", null);
		assertThat(statusOf(hers)).isEqualTo("RESOLVED");
		assertThat(refusal(() -> tickets.resolve(as(mary), hers, "again", null))).isEqualTo(ErrorCode.TICKET_CHANGED);
		long open = other(mary, "Open");
		assertThat(refusal(() -> tickets.resolve(as(mary), open, "too soon", null))).isEqualTo(ErrorCode.TICKET_CHANGED);
	}

	@Test
	void anOperatorCancelsOnlyTheirOwnTicketAndAFinalTicketNeverMoves() {
		long created = other(olive, "Opened by olive");
		long byOtto = other(otto, "Opened by otto");
		long resolved = ticketRow("RESOLVED", mary, olive, olive);
		long closed = ticketRow("CLOSED", mary, olive, olive);

		assertThat(refusal(() -> tickets.cancel(as(olive), byOtto, "dup", null))).isEqualTo(ErrorCode.NOT_ALLOWED);
		tickets.cancel(as(olive), created, "opened by mistake", null);
		assertThat(ticket(created)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "opened by mistake")
				.containsEntry("cancelled_by", olive);

		assertThat(refusal(() -> tickets.cancel(as(mary), resolved, "late", null))).isEqualTo(ErrorCode.TICKET_CHANGED);
		assertThat(refusal(() -> tickets.cancel(as(mary), closed, "late", null))).isEqualTo(ErrorCode.TICKET_CHANGED);
		assertThat(refusal(() -> tickets.take(as(otto), created, null))).as("a cancelled ticket is never taken").isEqualTo(ErrorCode.TICKET_CHANGED);
		assertThat(refusal(() -> tickets.take(as(mary), created, null))).isEqualTo(ErrorCode.TICKET_CHANGED);

		// Once taken by someone else, the creator can no longer cancel it.
		long taken = other(olive, "Taken by otto");
		tickets.take(as(otto), taken, null);
		assertThat(refusal(() -> tickets.cancel(as(olive), taken, "r", null))).isEqualTo(ErrorCode.NOT_FOUND);
		tickets.cancel(as(mary), taken, "duplicate", null);
	}

	@Test
	void aManagerArchivesOneTicketOrABatchAndSkipsTheOnesThatMovedOn() throws Exception {
		long first = ticketRow("RESOLVED", mary, olive, olive);
		long second = ticketRow("RESOLVED", mary, olive, olive);
		long open = ticketRow("OPEN", mary, null, null);
		long single = ticketRow("RESOLVED", mary, olive, olive);

		postAs("olive", "/tickets/" + single + "/archive", null).andExpect(status().isForbidden());
		postAs("mary", "/tickets/" + single + "/archive", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
				.andExpect(jsonPath("$.closedBy.username").value("mary"));
		assertThat(logs(single).get(0)).containsEntry("remark", "manual");
		assertThat((String) logs(single).get(0).get("after_data")).doesNotContain("batchId");

		postAs("mary", "/tickets/archive", "{\"ids\":[" + first + "," + open + "," + second + ",999999]}").andExpect(status().isOk())
				.andExpect(jsonPath("$.archived").value(contains(numberOf(first), numberOf(second))))
				.andExpect(jsonPath("$.skipped[0].ticketNo").value(numberOf(open))).andExpect(jsonPath("$.skipped[0].status").value("OPEN"))
				.andExpect(jsonPath("$.skipped[1].id").value(999999)).andExpect(jsonPath("$.skipped[1].ticketNo").isEmpty());
		String batchOfFirst = JsonPath.read((String) logs(first).get(0).get("after_data"), "$.batchId");
		String batchOfSecond = JsonPath.read((String) logs(second).get(0).get("after_data"), "$.batchId");
		assertThat(batchOfFirst).isEqualTo(batchOfSecond);
		assertThat(statusOf(open)).isEqualTo("OPEN");

		postAs("mary", "/tickets/archive", "{\"ids\":[]}").andExpect(status().isBadRequest());
	}

	@Test
	void theJobArchivesAfterSevenDaysAndAllAt99ResolvedAsTheSystem() {
		Instant now = Instant.parse("2026-11-16T00:00:00Z");
		clock.set(now);
		long old = ticketRow("RESOLVED", mary, olive, olive);
		long fresh = ticketRow("RESOLVED", mary, olive, olive);
		root().update("UPDATE tickets SET resolved_at = ? WHERE id = ?", UtcTime.toLocalDateTime(now.minus(Duration.ofDays(7))), old);
		root().update("UPDATE tickets SET resolved_at = ? WHERE id = ?", UtcTime.toLocalDateTime(now.minus(Duration.ofDays(6))), fresh);

		job.run();
		assertThat(statusOf(old)).isEqualTo("CLOSED");
		assertThat(statusOf(fresh)).isEqualTo("RESOLVED");
		assertThat(ticket(old)).containsEntry("closed_by", systemIdentity());
		assertThat(logs(old).get(0)).containsEntry("operator_id", null).containsEntry("remark", TicketArchiveJob.AFTER_DELAY);

		for (int i = 0; i < 98; i++) {
			long id = ticketRow("RESOLVED", mary, olive, olive);
			root().update("UPDATE tickets SET resolved_at = ? WHERE id = ?", UtcTime.toLocalDateTime(now.minus(Duration.ofHours(1))), id);
		}
		assertThat(count("SELECT COUNT(*) FROM tickets WHERE status = 'RESOLVED'")).isEqualTo(99);
		job.run();
		assertThat(count("SELECT COUNT(*) FROM tickets WHERE status = 'RESOLVED'")).isZero();
		assertThat(logs(fresh).get(0)).containsEntry("remark", TicketArchiveJob.AT_COUNT);
	}

	// Who sees what

	@Test
	void anOperatorSeesThePoolAndOnlyTheirOwnTicketsElsewhere() throws Exception {
		long pool = other(mary, "In the pool");
		long mine = ticketRow("IN_PROGRESS", mary, olive, null);
		long ottos = ticketRow("IN_PROGRESS", mary, otto, null);
		long resolvedByOtto = ticketRow("RESOLVED", mary, otto, otto);
		long cancelledMine = ticketRow("CANCELLED", olive, null, null);
		long cancelledOther = ticketRow("CANCELLED", mary, null, null);

		getAs("olive", "/tickets?status=OPEN").andExpect(jsonPath("$.items[*].id").value(contains((int) pool)));
		getAs("olive", "/tickets?status=IN_PROGRESS").andExpect(jsonPath("$.items[*].id").value(contains((int) mine)));
		getAs("olive", "/tickets?status=RESOLVED").andExpect(jsonPath("$.items.length()").value(0));
		getAs("olive", "/tickets?status=CANCELLED").andExpect(jsonPath("$.items[*].id").value(contains((int) cancelledMine)));
		getAs("olive", "/tickets?status=CLOSED").andExpect(status().isForbidden());
		getAs("olive", "/tickets?status=OPEN&source=MANUAL").andExpect(status().isForbidden());

		getAs("olive", "/tickets/" + ottos).andExpect(status().isNotFound());
		getAs("olive", "/tickets/" + resolvedByOtto).andExpect(status().isNotFound());
		getAs("olive", "/tickets/" + cancelledOther).andExpect(status().isNotFound());
		getAs("olive", "/tickets/" + mine).andExpect(status().isOk());

		getAs("vera", "/tickets?status=IN_PROGRESS").andExpect(jsonPath("$.items.length()").value(2));
		getAs("vera", "/tickets/" + ottos).andExpect(status().isOk());
		getAs("vera", "/tickets?status=CLOSED").andExpect(status().isOk());
		getAs("olive", "/tickets").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("status"));
	}

	@Test
	void systemTicketsComeFirstInEitherDirectionAndThePagesFollowTheKey() throws Exception {
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			long id = ticketRow("OPEN", mary, null, null);
			root().update("UPDATE tickets SET created_at = ? WHERE id = ?", LocalDateTime.of(2026, 11, 1, 0, 0).plusMinutes(i), id);
			ids.add(id);
		}
		root().update("UPDATE tickets SET source = 'SYSTEM', created_by = ? WHERE id IN (?, ?)", systemIdentity(), ids.get(2), ids.get(9));

		String first = getAs("vera", "/tickets?status=OPEN").andExpect(jsonPath("$.items[0].id").value(ids.get(9)))
				.andExpect(jsonPath("$.items[1].id").value(ids.get(2))).andExpect(jsonPath("$.items[1].createdBy.system").value(true))
				.andExpect(jsonPath("$.items[2].id").value(ids.get(11))).andReturn().getResponse().getContentAsString();
		String next = JsonPath.read(first, "$.nextCursor");
		String second = getAs("vera", "/tickets?status=OPEN&cursor=" + next).andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[1].id").value(ids.get(0))).andReturn().getResponse().getContentAsString();
		getAs("vera", "/tickets?status=OPEN&direction=prev&cursor=" + JsonPath.read(second, "$.prevCursor"))
				.andExpect(jsonPath("$.items.length()").value(10)).andExpect(jsonPath("$.items[0].id").value(ids.get(9)));

		getAs("vera", "/tickets?status=OPEN&order=asc").andExpect(jsonPath("$.items[0].id").value(ids.get(2)))
				.andExpect(jsonPath("$.items[1].id").value(ids.get(9))).andExpect(jsonPath("$.items[2].id").value(ids.get(0)));
	}

	@Test
	void theSearchFiltersInsideTheScreenAndANumberIsUsedAlone() throws Exception {
		long ollies = other(olive, "By olive");
		other(otto, "By otto");
		tickets.take(as(otto), ollies, null);
		long open = other(olive, "Open by olive");

		getAs("mary", "/tickets?status=OPEN&createdBy=" + olive).andExpect(jsonPath("$.items[*].id").value(contains((int) open)));
		getAs("mary", "/tickets?status=IN_PROGRESS&assigneeId=" + otto).andExpect(jsonPath("$.items[*].id").value(contains((int) ollies)));
		getAs("mary", "/tickets?status=OPEN&ticketNo=" + numberOf(open).toLowerCase()).andExpect(jsonPath("$.items.length()").value(1));
		getAs("mary", "/tickets?status=IN_PROGRESS&ticketNo=" + numberOf(open)).andExpect(jsonPath("$.items.length()").value(0));
		getAs("mary", "/tickets?status=OPEN&ticketNo=" + numberOf(open) + "&source=MANUAL").andExpect(status().isBadRequest());
		getAs("mary", "/tickets?status=OPEN&source=SYSTEM").andExpect(jsonPath("$.items.length()").value(0));
		getAs("mary", "/tickets?status=OPEN&source=BOTH").andExpect(status().isBadRequest());
	}

	// What the other modules start

	@Test
	void disablingAUserReturnsTheirTicketsInProgressToThePoolAsTheSystem() throws Exception {
		long held = other(mary, "Held by olive");
		long done = other(mary, "Resolved by olive");
		long created = other(olive, "Opened by olive");
		tickets.take(as(olive), held, null);
		tickets.take(as(olive), done, null);
		tickets.resolve(as(olive), done, "done", null);

		postAs("adam", "/users/" + olive + "/disable", null).andExpect(status().isOk());

		assertThat(statusOf(held)).isEqualTo("OPEN");
		assertThat(assigneeOf(held)).isNull();
		assertThat(history(held).get(1)).containsEntry("from_user_id", olive).containsEntry("to_user_id", null)
				.containsEntry("changed_by", systemIdentity()).containsEntry("reason", "The assignee's account was disabled");
		assertThat(logs(held).get(2)).containsEntry("action", "TICKET_RELEASED").containsEntry("operator_id", null);
		assertThat(statusOf(done)).isEqualTo("RESOLVED");
		assertThat(statusOf(created)).isEqualTo("OPEN");

		long ottos = other(mary, "Held by otto");
		tickets.take(as(otto), ottos, null);
		mvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + otto).header("Authorization", "Bearer " + token("adam")))
				.andExpect(status().isNoContent());
		assertThat(statusOf(ottos)).isEqualTo("OPEN");
		assertThat(history(ottos).get(1)).containsEntry("reason", "The assignee's account was deleted");
	}

	@Test
	void aUserWhoLosesEveryRoleThatMayTakeTicketsGivesBackTheTicketsInProgress() throws Exception {
		long held = other(mary, "Held by olive");
		long second = other(mary, "Also held by olive");
		long done = other(mary, "Resolved by olive");
		tickets.take(as(olive), held, null);
		tickets.take(as(olive), second, null);
		tickets.take(as(olive), done, null);
		tickets.resolve(as(olive), done, "done", null);

		getAs("adam", "/users/" + olive + "/role-change?roles=VIEWER").andExpect(status().isOk())
				.andExpect(jsonPath("$.ticketsReturned").value(2));
		getAs("adam", "/users/" + olive + "/role-change?roles=OPERATOR,VIEWER").andExpect(jsonPath("$.ticketsReturned").value(0));
		assertThat(statusOf(held)).as("the preview changes nothing").isEqualTo("IN_PROGRESS");

		mvc.perform(MockMvcRequestBuilders.put("/api/v1/users/" + olive + "/roles").header("Authorization", "Bearer " + token("adam"))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"roles\":[\"VIEWER\"],\"expectedRoles\":[\"OPERATOR\"]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ticketsReturned").value(2));

		for (long id : List.of(held, second)) {
			assertThat(statusOf(id)).isEqualTo("OPEN");
			assertThat(assigneeOf(id)).isNull();
			assertThat(history(id).get(1)).containsEntry("from_user_id", olive).containsEntry("changed_by", systemIdentity())
					.containsEntry("reason", "The assignee no longer has a role that may take tickets");
			assertThat(logs(id).get(2)).containsEntry("action", "TICKET_RELEASED").containsEntry("operator_id", null);
		}
		assertThat(statusOf(done)).isEqualTo("RESOLVED");
	}

	@Test
	void aUserWhoKeepsARoleThatMayTakeTicketsKeepsThem() throws Exception {
		long held = other(olive, "Held by mary");
		tickets.take(as(mary), held, null);

		mvc.perform(MockMvcRequestBuilders.put("/api/v1/users/" + mary + "/roles").header("Authorization", "Bearer " + token("adam"))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"roles\":[\"OPERATOR\"],\"expectedRoles\":[\"MANAGER\"]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ticketsReturned").value(0));
		assertThat(assigneeOf(held)).isEqualTo(mary);
		getAs("adam", "/users/999999/role-change?roles=VIEWER").andExpect(status().isNotFound());
		getAs("adam", "/users/" + olive + "/role-change?roles=NOBODY").andExpect(status().isBadRequest());
		getAs("olive", "/users/" + mary + "/role-change?roles=VIEWER").andExpect(status().isForbidden());
	}

	@Test
	void deletingAnItemCancelsItsLiveTicketsAndThePageNamesTheItem() throws Exception {
		long paper = item("A4 paper", mary);
		long open = onItems(olive, paper);
		long held = onItems(olive, paper);
		long done = onItems(olive, paper);
		tickets.take(as(otto), held, null);
		tickets.take(as(otto), done, null);
		tickets.resolve(as(otto), done, "fixed", null);

		mvc.perform(MockMvcRequestBuilders.delete("/api/v1/items/" + paper).header("Authorization", "Bearer " + token("mary")))
				.andExpect(status().isNoContent());

		assertThat(ticket(open)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "ITEM_DELETED:" + paper)
				.containsEntry("cancelled_by", systemIdentity());
		assertThat(ticket(held)).containsEntry("status", "CANCELLED").containsEntry("assignee_id", otto);
		assertThat(statusOf(done)).isEqualTo("RESOLVED");
		assertThat(logs(open).get(1)).containsEntry("action", "TICKET_CANCELLED").containsEntry("operator_id", null);
		assertThat(root().queryForObject("SELECT CAST(after_data AS CHAR) FROM operation_logs WHERE action = 'ITEM_DELETED'", String.class))
				.contains("\"cancelledTicketIds\": [" + open + ", " + held + "]")
				.contains("\"cancelledTicketNos\": [\"" + numberOf(open) + "\", \"" + numberOf(held) + "\"]");

		getAs("olive", "/tickets/" + open).andExpect(jsonPath("$.cancelCause.kind").value("ITEM_DELETED"))
				.andExpect(jsonPath("$.cancelCause.name").value("A4 paper")).andExpect(jsonPath("$.cancelledBy.system").value(true))
				.andExpect(jsonPath("$.items[0].deleted").value(true));
		// The last holder still sees it in the cancelled area.
		getAs("otto", "/tickets/" + held).andExpect(status().isOk());
	}

	@Test
	void delistingAProductCancelsTheLiveTicketsOfItsItem() throws Exception {
		long paper = item("A4 paper", mary);
		String answer = postAs("mary", "/products", "{\"inventoryId\":" + paper + ",\"name\":\"Paper A4\",\"price\":\"12.50\"}")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long product = ((Number) JsonPath.read(answer, "$.id")).longValue();
		long open = onItems(olive, paper);

		postAs("mary", "/products/" + product + "/delist", null).andExpect(status().isOk());

		assertThat(ticket(open)).containsEntry("status", "CANCELLED").containsEntry("cancel_reason", "PRODUCT_DELISTED:" + product);
		getAs("olive", "/tickets/" + open).andExpect(jsonPath("$.cancelCause.kind").value("PRODUCT_DELISTED"))
				.andExpect(jsonPath("$.cancelCause.name").value("Paper A4"));
		// A ticket opened after the delisting stays.
		long later = onItems(olive, paper);
		assertThat(statusOf(later)).isEqualTo("OPEN");
	}

	// One item, and no row without a reason

	@Test
	void theTicketsOfOneItemAreFoundAndNoRowIsLeftWithoutAReason() throws Exception {
		long paper = item("A4 paper", mary);
		long pens = item("Pens", mary);
		long onPaper = onItems(olive, paper);
		onItems(olive, pens);

		getAs("mary", "/tickets?status=OPEN&inventoryId=" + paper).andExpect(jsonPath("$.items[*].id").value(contains((int) onPaper)));
		getAs("mary", "/tickets?status=OPEN&inventoryId=" + paper + "&source=MANUAL").andExpect(jsonPath("$.items.length()").value(1));
		getAs("mary", "/tickets?status=OPEN&inventoryId=" + paper + "&ticketNo=" + numberOf(onPaper)).andExpect(status().isBadRequest());
		getAs("olive", "/tickets?status=OPEN&inventoryId=" + paper).andExpect(status().isForbidden());

		tickets.take(as(otto), onPaper, null);
		tickets.resolve(as(otto), onPaper, "counted", null);
		assertThat(history(onPaper).get(0)).containsEntry("reason", "Taken from the pool");
		assertThat(logs(onPaper).get(0)).containsEntry("remark", "Opened on the ticket page");
		assertThat(count("SELECT COUNT(*) FROM operation_logs WHERE remark IS NULL OR TRIM(remark) = ''")).as("no log row without a reason").isZero();
		assertThat(count("SELECT COUNT(*) FROM ticket_assignment_history WHERE reason IS NULL OR TRIM(reason) = ''")).isZero();
	}

	@Test
	void theUserPageShowsDeletedUsersOnRequestAndTheAccountRowsNameTheTicketsReturned() throws Exception {
		long held = other(mary, "Held by olive");
		tickets.take(as(olive), held, null);

		getAs("adam", "/users?search=olive").andExpect(jsonPath("$.items[0].ticketsInProgress").value(1))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
		postAs("adam", "/users/" + olive + "/disable", null).andExpect(status().isOk()).andExpect(jsonPath("$.ticketsReturned").value(1));
		assertThat(root().queryForObject("SELECT CAST(after_data AS CHAR) FROM operation_logs WHERE action = 'USER_STATUS_CHANGED' AND target_id = ?",
				String.class, olive)).contains("\"returnedTicketNos\": [\"" + numberOf(held) + "\"]");

		getAs("adam", "/audit-records?area=ACCOUNT&aboutUserId=" + olive).andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].remark").value("Disabled on the user page"));
		getAs("adam", "/audit-records?area=ACCOUNT&aboutUserId=" + otto).andExpect(jsonPath("$.items.length()").value(0));
		getAs("adam", "/audit-records?area=TICKET&aboutUserId=" + olive).andExpect(status().isBadRequest());

		mvc.perform(MockMvcRequestBuilders.delete("/api/v1/users/" + otto).header("Authorization", "Bearer " + token("adam")))
				.andExpect(status().isNoContent());
		getAs("adam", "/users").andExpect(jsonPath("$.items[*].username").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("otto"))));
		getAs("adam", "/users?status=DELETED").andExpect(jsonPath("$.items[*].username").value(contains("otto")))
				.andExpect(jsonPath("$.items[0].status").value("DELETED"));
		getAs("adam", "/users?status=DISABLED,DELETED").andExpect(jsonPath("$.items[*].username").value(contains("olive", "otto")));
		getAs("adam", "/users?status=GONE").andExpect(status().isBadRequest());
	}

	// The user picker

	@Test
	void theUserPickerOffersEveryoneForAFilterAndOnlyEligibleAssigneesForAnAssignment() throws Exception {
		long gone = account("gina", "OPERATOR");
		root().update("UPDATE users SET status = 'DISABLED', deleted = TRUE WHERE id = ?", gone);

		getAs("vera", "/user-options?use=filter").andExpect(jsonPath("$.items[*].username")
				.value(contains("adam", "gina", "mary", "olive", "otto", "vera")));
		getAs("mary", "/user-options?use=assignee").andExpect(jsonPath("$.items[*].username").value(contains("mary", "olive", "otto")));
		getAs("adam", "/user-options?use=assignee&search=A").andExpect(jsonPath("$.items[*].username").value(contains("adam", "mary")));
		getAs("vera", "/user-options?use=assignee").andExpect(status().isForbidden());
		getAs("olive", "/user-options?use=filter").andExpect(status().isForbidden());
		getAs("mary", "/user-options?use=everyone").andExpect(status().isBadRequest());
		getAs("mary", "/user-options").andExpect(status().isBadRequest());
	}

	@Test
	void theTimesOfAChangeComeFromTheApplicationClock() {
		Instant at = Instant.parse("2026-11-10T03:00:00.123Z");
		clock.set(at);
		long id = other(mary, "Timed");
		tickets.take(as(olive), id, null);
		tickets.resolve(as(olive), id, "done", null);
		assertThat(UtcTime.toInstant((LocalDateTime) ticket(id).get("resolved_at"))).isEqualTo(at.truncatedTo(ChronoUnit.MILLIS));
		assertThat(UtcTime.toInstant((LocalDateTime) ticket(id).get("created_at"))).isEqualTo(at.truncatedTo(ChronoUnit.MILLIS));
	}

}
