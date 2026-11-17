package com.opsflow.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.paging.Cursor;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.time.DayRanges;
import com.opsflow.common.time.DayRanges.Range;
import com.opsflow.common.validation.Limits;
import com.opsflow.ticket.TicketRepository.Detail;
import com.opsflow.ticket.TicketRepository.Filter;
import com.opsflow.ticket.TicketRepository.HistoryRow;
import com.opsflow.ticket.TicketRepository.LinkedItem;
import com.opsflow.ticket.TicketRepository.Listed;
import com.opsflow.ticket.TicketRepository.Related;
import com.opsflow.ticket.TicketWriter.NewTicket;
import com.opsflow.ticket.TicketWriter.NumberTaken;

/**
 * The ticket module (see Tickets in docs/api/api-design.md). It checks the shape of a request before any
 * transaction, runs each change through {@link TransactionRetry}, and answers with the ticket as it is
 * after the change. It reads the lists and the detail with what the caller may see, filtered here and in
 * the query, never only in the page.
 */
@Service
public class TicketService {

	private static final Set<String> TYPES = Set.of("ORDER_EXCEPTION", "INVENTORY_EXCEPTION", "OTHER");
	private static final Set<String> SOURCES = Set.of("SYSTEM", "MANUAL");
	/** The reason the system writes when it cancels the tickets of a delisted product or a deleted item. */
	private static final Pattern CAUSE = Pattern.compile("(PRODUCT_DELISTED|ITEM_DELETED):(\\d+)");

	/** A ticket that this one follows or that follows it. {@code id} is null when the caller may not see it. */
	public record Ref(Long id, String ticketNo) {
	}

	/** Why the system cancelled a ticket, for the message of the page: the kind and the name of the product or item. */
	public record CancelCause(String kind, String name) {
	}

	/** One ticket with everything the detail shows. */
	public record TicketDetail(Detail ticket, List<LinkedItem> items, Ref follows, List<Ref> followUps, List<HistoryRow> history,
			CancelCause cancelCause) {
	}

	/** A ticket of a batch archive that was not archived, with its status now (null when it does not exist or is hidden). */
	public record Skipped(long id, String ticketNo, String status) {
	}

	public record BatchResult(List<String> archived, List<Skipped> skipped) {
	}

	/** The search of a ticket screen, as it was sent. */
	public record Search(String source, Long createdBy, Long assigneeId, Long inventoryId, String from, String to, String ticketNo,
			String orderNo) {

		boolean any() {
			return source != null || createdBy != null || assigneeId != null || inventoryId != null || from != null || to != null
					|| ticketNo != null
					|| orderNo != null;
		}
	}

	private final TicketWriter writer;
	private final TicketRepository repository;
	private final TicketNumbers numbers;
	private final TransactionRetry retry;
	private final DayRanges days;
	private final int numberAttempts;
	private final int maxItems;
	private final int maxArchiveBatch;

	TicketService(TicketWriter writer, TicketRepository repository, TicketNumbers numbers, TransactionRetry retry, DayRanges days,
			OpsflowProperties properties) {
		this.writer = writer;
		this.repository = repository;
		this.numbers = numbers;
		this.retry = retry;
		this.days = days;
		this.numberAttempts = properties.ticket().numberAttempts();
		this.maxItems = properties.ticket().maxItems();
		this.maxArchiveBatch = properties.ticket().maxArchiveBatch();
	}

	// Opening a ticket

	public TicketDetail create(CurrentUser actor, String type, String title, String description, List<Long> inventoryIds, String orderNo,
			String followsTicketNo, String ipAddress) {
		NewTicket ticket = checked(type, title, description, inventoryIds, orderNo, followsTicketNo);
		for (int attempt = 1; attempt <= numberAttempts; attempt++) {
			String number = numbers.next();
			try {
				long id = retry.run(() -> writer.create(actor.id(), number, ticket, ipAddress));
				return detail(actor, id);
			}
			catch (NumberTaken clash) {
				// Another ticket has the number: make another.
			}
		}
		throw new ApiException(ErrorCode.INTERNAL_ERROR, "No free ticket number was found in " + numberAttempts + " attempts");
	}

	private NewTicket checked(String type, String title, String description, List<Long> inventoryIds, String orderNo, String followsTicketNo) {
		if (type == null || !TYPES.contains(type)) {
			throw ApiException.invalid("type", "must be ORDER_EXCEPTION, INVENTORY_EXCEPTION, or OTHER");
		}
		String checkedTitle = text("title", title, Limits.TICKET_TITLE);
		String checkedDescription = text("description", description, Limits.TICKET_DESCRIPTION);

		List<Long> ids = inventoryIds == null ? List.of() : inventoryIds;
		if (ids.size() > maxItems) {
			throw ApiException.invalid("inventoryIds", "must hold at most " + maxItems + " items");
		}
		Set<Long> seen = new HashSet<>();
		for (Long id : ids) {
			if (id == null || id < 1) {
				throw ApiException.invalid("inventoryIds", "must hold the ids of items");
			}
			if (!seen.add(id)) {
				throw ApiException.invalid("inventoryIds", "names an item twice");
			}
		}
		if ("INVENTORY_EXCEPTION".equals(type) && ids.isEmpty()) {
			throw ApiException.invalid("inventoryIds", "must hold at least one item for an inventory exception");
		}
		List<Long> sorted = new ArrayList<>(ids);
		sorted.sort(null);

		String number = orderNo(orderNo);
		if ("ORDER_EXCEPTION".equals(type) && number == null) {
			throw ApiException.invalid("orderNo", "is required for an order exception");
		}

		String follows = null;
		if (followsTicketNo != null && !followsTicketNo.isBlank()) {
			follows = TicketNumbers.typed(followsTicketNo);
			if (follows == null) {
				throw new ApiException(ErrorCode.FOLLOW_NOT_FOUND, "No ticket has that number");
			}
		}
		return new NewTicket(type, checkedTitle, checkedDescription, List.copyOf(sorted), number, follows);
	}

	// Changing a ticket

	public TicketDetail take(CurrentUser actor, long ticketId, String ipAddress) {
		retry.run(() -> writer.take(actor, ticketId, ipAddress));
		return detail(actor, ticketId);
	}

	public TicketDetail assign(CurrentUser actor, long ticketId, Long assigneeId, String reason, String ipAddress) {
		long assignee = userId("assigneeId", assigneeId);
		String checkedReason = text("reason", reason, Limits.REASON);
		retry.run(() -> writer.assign(actor, ticketId, assignee, checkedReason, ipAddress));
		return detail(actor, ticketId);
	}

	public TicketDetail reassign(CurrentUser actor, long ticketId, Long assigneeId, Long expectedAssigneeId, String reason, String ipAddress) {
		long assignee = userId("assigneeId", assigneeId);
		long expected = userId("expectedAssigneeId", expectedAssigneeId);
		String checkedReason = text("reason", reason, Limits.REASON);
		retry.run(() -> writer.reassign(actor, ticketId, assignee, expected, checkedReason, ipAddress));
		return detail(actor, ticketId);
	}

	public TicketDetail release(CurrentUser actor, long ticketId, Long expectedAssigneeId, String reason, String ipAddress) {
		long expected = userId("expectedAssigneeId", expectedAssigneeId);
		String checkedReason = text("reason", reason, Limits.REASON);
		retry.run(() -> writer.release(actor, ticketId, expected, checkedReason, ipAddress));
		return detail(actor, ticketId);
	}

	public TicketDetail resolve(CurrentUser actor, long ticketId, String resolution, String ipAddress) {
		String checked = text("resolution", resolution, Limits.TICKET_RESOLUTION);
		retry.run(() -> writer.resolve(actor, ticketId, checked, ipAddress));
		return detail(actor, ticketId);
	}

	public TicketDetail cancel(CurrentUser actor, long ticketId, String reason, String ipAddress) {
		String checkedReason = text("reason", reason, Limits.REASON);
		retry.run(() -> writer.cancel(actor, ticketId, checkedReason, ipAddress));
		return detail(actor, ticketId);
	}

	public TicketDetail archive(CurrentUser actor, long ticketId, String ipAddress) {
		retry.run(() -> writer.archive(actor, ticketId, null, "manual", ipAddress));
		return detail(actor, ticketId);
	}

	/** Archives each ticket in its own transaction; a ticket that is no longer {@code RESOLVED} is skipped. */
	public BatchResult archiveBatch(CurrentUser actor, List<Long> ids, String ipAddress) {
		if (ids == null || ids.isEmpty() || ids.size() > maxArchiveBatch) {
			throw ApiException.invalid("ids", "must hold 1 to " + maxArchiveBatch + " tickets");
		}
		Set<Long> seen = new HashSet<>();
		for (Long id : ids) {
			if (id == null || id < 1) {
				throw ApiException.invalid("ids", "must hold the ids of tickets");
			}
			if (!seen.add(id)) {
				throw ApiException.invalid("ids", "names a ticket twice");
			}
		}
		String batchId = UUID.randomUUID().toString();
		List<String> archived = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		for (long id : ids) {
			try {
				archived.add(retry.run(() -> writer.archive(actor, id, batchId, "batch", ipAddress)));
			}
			catch (ApiException refused) {
				if (refused.code() != ErrorCode.TICKET_CHANGED && refused.code() != ErrorCode.NOT_FOUND && refused.code() != ErrorCode.BUSY) {
					throw refused;
				}
				Related now = repository.related(id).filter(ticket -> TicketAccess.visible(actor, ticket)).orElse(null);
				skipped.add(now == null ? new Skipped(id, null, null) : new Skipped(id, now.ticketNo(), now.status().name()));
			}
		}
		return new BatchResult(archived, skipped);
	}

	// Reading

	public PageResponse<Listed> list(CurrentUser user, String status, Search search, PageQuery page) {
		TicketStatus screen = status(status);
		boolean all = user.can(Permission.TICKET_VIEW_ALL);
		if (screen == TicketStatus.CLOSED && !all) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Your roles do not open the archive");
		}
		if (search.any() && !user.can(Permission.TICKET_SEARCH)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Your roles do not allow searching the tickets");
		}
		String ticketNo = null;
		if (search.ticketNo() != null && !search.ticketNo().isBlank()) {
			ticketNo = TicketNumbers.typed(search.ticketNo());
			if (ticketNo == null) {
				throw ApiException.invalid("ticketNo", "must be 8 letters and digits");
			}
		}
		String orderNo = orderNo(search.orderNo());
		boolean filtered = search.source() != null || search.createdBy() != null || search.assigneeId() != null
				|| search.inventoryId() != null || search.from() != null
				|| search.to() != null;
		if (ticketNo != null && (filtered || orderNo != null)) {
			throw ApiException.invalid("ticketNo", "is used alone, with no other filter");
		}
		if (orderNo != null && filtered) {
			throw ApiException.invalid("orderNo", "is used alone, with no other filter");
		}
		String source = null;
		if (search.source() != null) {
			source = search.source().strip().toUpperCase(Locale.ROOT);
			if (!SOURCES.contains(source)) {
				throw ApiException.invalid("source", "must be SYSTEM or MANUAL");
			}
		}
		Range range = days.parse(search.from(), search.to());
		Filter filter = new Filter(screen, all ? null : user.id(), source, optionalId("createdBy", search.createdBy()),
				optionalId("assigneeId", search.assigneeId()), itemId(search.inventoryId()), range, ticketNo, orderNo);
		return PageResponse.of(repository.list(filter, page), page,
				row -> new Cursor(TicketRepository.cursorKeyOf(row.source(), sortTime(screen, row)), row.id()));
	}

	public TicketDetail detail(CurrentUser user, long id) {
		Detail ticket = repository.detail(id).orElseThrow(TicketWriter::notFound);
		Long assigneeId = ticket.assignee() == null ? null : ticket.assignee().id();
		if (!TicketAccess.visible(user, ticket.status(), ticket.createdBy().id(), assigneeId, ticket.resolvedById())) {
			throw TicketWriter.notFound();
		}
		Ref follows = null;
		if (ticket.followsTicketId() != null) {
			follows = repository.related(ticket.followsTicketId()).map(related -> ref(user, related)).orElse(null);
		}
		List<Ref> followUps = repository.followUps(id).stream().map(related -> ref(user, related)).toList();
		return new TicketDetail(ticket, repository.items(id), follows, followUps, repository.history(id), cause(ticket));
	}

	// Helpers

	private static Ref ref(CurrentUser user, Related related) {
		return new Ref(TicketAccess.visible(user, related) ? related.id() : null, related.ticketNo());
	}

	private CancelCause cause(Detail ticket) {
		if (ticket.cancelReason() == null || ticket.cancelledBy() == null || !ticket.cancelledBy().system()) {
			return null;
		}
		Matcher matcher = CAUSE.matcher(ticket.cancelReason());
		if (!matcher.matches()) {
			return null;
		}
		long id = Long.parseLong(matcher.group(2));
		String name = "PRODUCT_DELISTED".equals(matcher.group(1)) ? repository.productName(id).orElse(null)
				: repository.itemNameOf(id).orElse(null);
		return new CancelCause(matcher.group(1), name);
	}

	private static Instant sortTime(TicketStatus screen, Listed row) {
		return switch (screen) {
			case CLOSED -> row.closedAt();
			case CANCELLED -> row.cancelledAt();
			default -> row.createdAt();
		};
	}

	private static TicketStatus status(String status) {
		if (status == null || status.isBlank()) {
			throw ApiException.invalid("status", "is required: OPEN, IN_PROGRESS, RESOLVED, CLOSED, or CANCELLED");
		}
		try {
			return TicketStatus.of(status.strip().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException unknown) {
			throw ApiException.invalid("status", "must be OPEN, IN_PROGRESS, RESOLVED, CLOSED, or CANCELLED");
		}
	}

	/** A text a person typed, without the spaces around it: 1 to the limit characters. */
	private static String text(String field, String value, int limit) {
		String trimmed = value == null ? "" : value.strip();
		if (trimmed.isEmpty() || trimmed.length() > limit) {
			throw ApiException.invalid(field, "must be 1 to " + limit + " characters");
		}
		return trimmed;
	}

	private static long userId(String field, Long id) {
		if (id == null || id < 1) {
			throw ApiException.invalid(field, "must be the id of a user");
		}
		return id;
	}

	private static Long itemId(Long id) {
		if (id != null && id < 1) {
			throw ApiException.invalid("inventoryId", "must be the id of an item");
		}
		return id;
	}

	private static Long optionalId(String field, Long id) {
		if (id != null && id < 1) {
			throw ApiException.invalid(field, "must be the id of a user");
		}
		return id;
	}

	/**
	 * An order number, or null when none was typed. It is written as the numbers are made, whatever case was
	 * typed: {@code ORD-} in upper case and the rest in lower case, because the column compares exactly.
	 */
	private static String orderNo(String orderNo) {
		if (orderNo == null || orderNo.isBlank()) {
			return null;
		}
		String number = orderNo.strip().toLowerCase(Locale.ROOT);
		if (number.startsWith("ord-")) {
			number = "ORD-" + number.substring(4);
		}
		if (number.length() > Limits.ORDER_NO) {
			throw ApiException.invalid("orderNo", "must be at most " + Limits.ORDER_NO + " characters");
		}
		return number;
	}

}
