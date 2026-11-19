package com.opsflow.ticket;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageQueryFactory;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.RequiresPermission;
import com.opsflow.ticket.TicketRepository.Detail;
import com.opsflow.ticket.TicketRepository.HistoryRow;
import com.opsflow.ticket.TicketRepository.LinkedItem;
import com.opsflow.ticket.TicketRepository.Listed;
import com.opsflow.ticket.TicketRepository.Person;
import com.opsflow.ticket.TicketService.BatchResult;
import com.opsflow.ticket.TicketService.CancelCause;
import com.opsflow.ticket.TicketService.Ref;
import com.opsflow.ticket.TicketService.Search;
import com.opsflow.ticket.TicketService.TicketDetail;

import jakarta.servlet.http.HttpServletRequest;

/** The ticket screens and every change of a ticket. See Tickets in docs/api/api-design.md. */
@RestController
@RequestMapping("/tickets")
public class TicketController {

	public record CreateTicketRequest(String type, String title, String description, List<Long> inventoryIds, String orderNo,
			String followsTicketNo) {
	}

	public record AssignRequest(Long assigneeId, String reason) {
	}

	public record ReassignRequest(Long assigneeId, Long expectedAssigneeId, String reason) {
	}

	public record ReleaseRequest(Long expectedAssigneeId, String reason) {
	}

	public record ResolveRequest(String resolution) {
	}

	public record CancelRequest(String reason) {
	}

	public record ArchiveBatchRequest(List<Long> ids) {
	}

	/**
	 * A row of a ticket list. {@code person} and {@code time} belong to the step the screen is about: the
	 * creator and the creation in the pool, the assignee and when they got the ticket in progress, then who
	 * resolved, archived, or cancelled it, and when.
	 */
	public record TicketRow(long id, String ticketNo, String type, String source, String title, String status, Person person, Instant time) {

		static TicketRow of(Listed row) {
			return new TicketRow(row.id(), row.ticketNo(), row.type(), row.source(), row.title(), row.status().name(), row.person(), row.time());
		}
	}

	public record ItemRef(long inventoryId, String sku, String name, boolean deleted) {
	}

	public record HistoryEntry(Person from, Person to, Person by, String reason, Instant at) {
	}

	/** One ticket, as every read and every change answers it. */
	public record TicketResponse(long id, String ticketNo, String type, String source, String status, String title, String description,
			String orderNo, List<ItemRef> items, Ref follows, List<Ref> followUps, Person createdBy, Instant createdAt, Person assignee,
			List<HistoryEntry> history, String resolution, Person resolvedBy, Instant resolvedAt, Person closedBy, Instant closedAt,
			String cancelReason, CancelCause cancelCause, Person cancelledBy, Instant cancelledAt) {

		static TicketResponse of(TicketDetail detail) {
			Detail t = detail.ticket();
			List<ItemRef> items = detail.items().stream().map(TicketResponse::item).toList();
			List<HistoryEntry> history = detail.history().stream().map(TicketResponse::entry).toList();
			return new TicketResponse(t.id(), t.ticketNo(), t.type(), t.source(), t.status().name(), t.title(), t.description(), t.orderNo(),
					items, detail.follows(), detail.followUps(), t.createdBy(), t.createdAt(), t.assignee(), history, t.resolution(),
					t.resolvedBy(), t.resolvedAt(), t.closedBy(), t.closedAt(), t.cancelReason(), detail.cancelCause(), t.cancelledBy(),
					t.cancelledAt());
		}

		private static ItemRef item(LinkedItem item) {
			return new ItemRef(item.inventoryId(), item.sku(), item.name(), item.deleted());
		}

		private static HistoryEntry entry(HistoryRow row) {
			return new HistoryEntry(row.from(), row.to(), row.by(), row.reason(), row.at());
		}
	}

	private final TicketService tickets;
	private final PageQueryFactory pages;

	TicketController(TicketService tickets, PageQueryFactory pages) {
		this.tickets = tickets;
		this.pages = pages;
	}

	/** One screen: the tickets of one status that the caller may see, with the search of the screen. */
	@RequiresPermission(Permission.TICKET_VIEW)
	@GetMapping
	public PageResponse<TicketRow> list(@RequestParam(required = false) String status, @RequestParam(required = false) String source,
			@RequestParam(required = false) Long createdBy, @RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) Long inventoryId,
			@RequestParam(required = false) String from, @RequestParam(required = false) String to,
			@RequestParam(required = false) String ticketNo, @RequestParam(required = false) String orderNo,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order,
			@AuthenticationPrincipal CurrentUser user) {
		PageQuery page = pages.create(limit, cursor, direction, order, true);
		PageResponse<Listed> found = tickets.list(user, status, new Search(source, createdBy, assigneeId, inventoryId, from, to, ticketNo, orderNo), page);
		return new PageResponse<>(found.items().stream().map(TicketRow::of).toList(), found.nextCursor(), found.prevCursor());
	}

	@RequiresPermission(Permission.TICKET_VIEW)
	@GetMapping("/{id}")
	public TicketResponse detail(@PathVariable long id, @AuthenticationPrincipal CurrentUser user) {
		return TicketResponse.of(tickets.detail(user, id));
	}

	@RequiresPermission(Permission.TICKET_CREATE)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketResponse create(@RequestBody CreateTicketRequest body, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		return TicketResponse.of(tickets.create(user, body.type(), body.title(), body.description(), body.inventoryIds(), body.orderNo(),
				body.followsTicketNo(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_TAKE)
	@PostMapping("/{id}/take")
	public TicketResponse take(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		return TicketResponse.of(tickets.take(user, id, request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_ASSIGN)
	@PostMapping("/{id}/assign")
	public TicketResponse assign(@PathVariable long id, @RequestBody AssignRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return TicketResponse.of(tickets.assign(user, id, body.assigneeId(), body.reason(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_ASSIGN)
	@PostMapping("/{id}/reassign")
	public TicketResponse reassign(@PathVariable long id, @RequestBody ReassignRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return TicketResponse.of(tickets.reassign(user, id, body.assigneeId(), body.expectedAssigneeId(), body.reason(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_RELEASE)
	@PostMapping("/{id}/release")
	public TicketResponse release(@PathVariable long id, @RequestBody ReleaseRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return TicketResponse.of(tickets.release(user, id, body.expectedAssigneeId(), body.reason(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_RESOLVE)
	@PostMapping("/{id}/resolve")
	public TicketResponse resolve(@PathVariable long id, @RequestBody ResolveRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return TicketResponse.of(tickets.resolve(user, id, body.resolution(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_CANCEL)
	@PostMapping("/{id}/cancel")
	public TicketResponse cancel(@PathVariable long id, @RequestBody CancelRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return TicketResponse.of(tickets.cancel(user, id, body.reason(), request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_ARCHIVE)
	@PostMapping("/{id}/archive")
	public TicketResponse archive(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		return TicketResponse.of(tickets.archive(user, id, request.getRemoteAddr()));
	}

	@RequiresPermission(Permission.TICKET_ARCHIVE)
	@PostMapping("/archive")
	public BatchResult archiveBatch(@RequestBody ArchiveBatchRequest body, @AuthenticationPrincipal CurrentUser user,
			HttpServletRequest request) {
		return tickets.archiveBatch(user, body.ids(), request.getRemoteAddr());
	}

}
