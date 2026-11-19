package com.opsflow.ticket;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;
import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.ticket.TicketRepository.Locked;
import com.opsflow.ticket.TicketRepository.Related;
import com.opsflow.ticket.TicketRepository.SharedItem;
import com.opsflow.ticket.TicketRepository.UserLock;

/**
 * The transactions that open and change a ticket (T-14, T-33, T-35, T-40; see Tickets and the ticket rows
 * of Concurrency by Module in docs/database/database-design.md). Every refusal is thrown, so the
 * transaction rolls back and nothing is written; the caller runs each method through
 * {@code TransactionRetry}.
 *
 * Lock order: the user rows the change needs ({@code FOR UPDATE}, ascending id), then the ticket row, and
 * for a new ticket the chosen items ({@code FOR SHARE}, ascending id) after the new ticket row. Every
 * change of status is a conditional update on the status as well (the status gate), and writes its
 * history row, if the assignee changes, and its log row in the same transaction.
 */
@Component
public class TicketWriter {

	/** The fields of a new ticket, checked. The item ids are in ascending order, each once. */
	record NewTicket(String type, String title, String description, List<Long> inventoryIds, String orderNo, String followsTicketNo) {
	}

	/** The number was issued before: the caller makes another and tries again. */
	static final class NumberTaken extends RuntimeException {

		private static final long serialVersionUID = 1L;

		NumberTaken() {
			super("The ticket number was issued before", null, false, false);
		}
	}

	/** The reason of the history row of a take, which asks the person for none (T-69). */
	static final String TAKEN = "Taken from the pool";

	private final TicketRepository repository;
	private final AuditLogWriter audit;
	private final Clock clock;
	private final int maxOpenCreated;
	private final int maxInProgress;
	private volatile Long systemUserId;

	TicketWriter(TicketRepository repository, AuditLogWriter audit, Clock clock, OpsflowProperties properties) {
		this.repository = repository;
		this.audit = audit;
		this.clock = clock;
		this.maxOpenCreated = properties.ticket().maxOpenCreated();
		this.maxInProgress = properties.ticket().maxInProgress();
	}

	/**
	 * Opens a ticket (see Creating a ticket on items). The creator's row is locked first, so two tickets of
	 * the same person count one after the other; the items are locked after the new ticket row.
	 *
	 * @return the id of the new ticket
	 */
	@Transactional
	public long create(long creatorId, String ticketNo, NewTicket ticket, String ipAddress) {
		UserLock creator = repository.lockUser(creatorId).orElse(null);
		if (creator == null || !creator.system()) {
			requireUsable(creator);
		}
		// The SYSTEM identity is exempt from the limit (T-36); it opens no ticket today.
		if (!creator.system() && repository.countOpenCreatedBy(creatorId) >= maxOpenCreated) {
			throw new ApiException(ErrorCode.LIMIT_REACHED, "You already have " + maxOpenCreated + " open tickets");
		}

		Long followsId = null;
		if (ticket.followsTicketNo() != null) {
			Related followed = repository.findByNo(ticket.followsTicketNo())
					.orElseThrow(() -> new ApiException(ErrorCode.FOLLOW_NOT_FOUND, "No ticket has that number"));
			if (followed.status() != TicketStatus.RESOLVED && followed.status() != TicketStatus.CLOSED) {
				throw new ApiException(ErrorCode.FOLLOW_NOT_ALLOWED, "That ticket cannot be followed yet: only a resolved or archived ticket can");
			}
			followsId = followed.id();
		}
		if (ticket.orderNo() != null && !repository.orderAttemptExists(ticket.orderNo())) {
			throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "No order has that number");
		}

		Instant now = now();
		String source = creator.system() ? "SYSTEM" : "MANUAL";
		long id;
		try {
			id = repository.insertTicket(ticketNo, ticket.type(), source, ticket.title(), ticket.description(), ticket.orderNo(), followsId,
					creatorId, now);
		}
		catch (DuplicateKeyException duplicate) {
			if (String.valueOf(duplicate.getMessage()).contains("uk_tickets_ticket_no")) {
				throw new NumberTaken();
			}
			throw duplicate;
		}

		// The items, shared, in ascending id; every one is locked before any is judged.
		List<Map<String, Object>> gone = new ArrayList<>();
		for (long inventoryId : ticket.inventoryIds()) {
			SharedItem item = repository.shareItem(inventoryId).orElse(null);
			if (item == null || item.deleted()) {
				Map<String, Object> named = new LinkedHashMap<>();
				named.put("inventoryId", inventoryId);
				named.put("name", item == null ? null : item.name());
				gone.add(named);
			}
		}
		if (!gone.isEmpty()) {
			throw new ApiException(ErrorCode.ITEM_CHANGED, "A chosen item no longer exists, please choose again").with("items", gone);
		}
		for (long inventoryId : ticket.inventoryIds()) {
			repository.insertLink(id, inventoryId, now);
		}

		Map<String, Object> after = new LinkedHashMap<>();
		after.put("type", ticket.type());
		after.put("source", source);
		after.put("createdBy", creatorId);
		after.put("followsTicketId", followsId);
		after.put("orderNo", ticket.orderNo());
		after.put("inventoryIds", ticket.inventoryIds());
		after.put("ticketNo", ticketNo);
		after.put("createdByUsername", creator.username());
		audit.write(AuditAction.TICKET_CREATED, creatorId, AuditLogWriter.TARGET_TICKET, id, null, after, ipAddress, null);
		return id;
	}

	/** The caller takes an {@code OPEN} ticket: it is {@code IN_PROGRESS} at once, with the caller as its assignee. */
	@Transactional
	public void take(CurrentUser actor, long ticketId, String ipAddress) {
		UserLock taker = usableActor(actor.id());
		List<String> roles = repository.roleCodes(actor.id());
		if (!TicketAccess.grants(roles, Permission.TICKET_TAKE)) {
			throw noRoleOrForbidden(roles, "Your roles no longer allow you to take tickets");
		}
		requireRoomInProgress(taker, "You already have " + maxInProgress + " tickets in progress");
		// The pool is open to everyone, so a ticket that just left it is answered as changed, not as missing.
		// Its status is told only to someone who may see the ticket now.
		Locked ticket = repository.lockTicket(ticketId).orElseThrow(TicketWriter::notFound);
		Instant now = now();
		if (ticket.status() != TicketStatus.OPEN || repository.assignFromPool(ticketId, actor.id(), now) == 0) {
			throw TicketAccess.visible(actor, ticket) ? changed(ticket.status())
					: new ApiException(ErrorCode.TICKET_CHANGED, "The ticket is no longer in the pool, please reload");
		}
		repository.insertHistory(ticketId, null, actor.id(), actor.id(), TAKEN, now);
		logAssigned(actor.id(), ticket, taker, "taken", ipAddress);
	}

	/** A manager or an administrator gives an {@code OPEN} ticket to an eligible assignee. */
	@Transactional
	public void assign(CurrentUser actor, long ticketId, long assigneeId, String reason, String ipAddress) {
		UserLock assignee = lockActorAndAssignee(actor.id(), assigneeId);
		requireRoomInProgress(assignee, assignee.username() + " already has " + maxInProgress + " tickets in progress");
		Locked ticket = lockVisible(actor, ticketId);
		Instant now = now();
		if (ticket.status() != TicketStatus.OPEN || repository.assignFromPool(ticketId, assigneeId, now) == 0) {
			throw changed(ticket.status());
		}
		repository.insertHistory(ticketId, null, assigneeId, actor.id(), reason, now);
		logAssigned(actor.id(), ticket, assignee, "assigned", ipAddress);
	}

	/**
	 * A manager or an administrator gives an {@code IN_PROGRESS} ticket to someone else. The assignee the actor
	 * saw must still be the assignee, under the lock of the ticket row.
	 */
	@Transactional
	public void reassign(CurrentUser actor, long ticketId, long assigneeId, long expectedAssigneeId, String reason, String ipAddress) {
		if (assigneeId == expectedAssigneeId) {
			throw new ApiException(ErrorCode.NOT_ELIGIBLE, "The ticket is already with this person");
		}
		UserLock assignee = lockActorAndAssignee(actor.id(), assigneeId);
		requireRoomInProgress(assignee, assignee.username() + " already has " + maxInProgress + " tickets in progress");
		Locked ticket = lockVisible(actor, ticketId);
		Instant now = now();
		if (ticket.status() != TicketStatus.IN_PROGRESS || !Objects.equals(ticket.assigneeId(), expectedAssigneeId)
				|| repository.reassign(ticketId, expectedAssigneeId, assigneeId, now) == 0) {
			throw changed(ticket.status());
		}
		repository.insertHistory(ticketId, expectedAssigneeId, assigneeId, actor.id(), reason, now);
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("assigneeId", expectedAssigneeId);
		before.put("assigneeUsername", repository.username(expectedAssigneeId));
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("assigneeId", assigneeId);
		after.put("assigneeUsername", assignee.username());
		after.put("ticketNo", ticket.ticketNo());
		audit.write(AuditAction.TICKET_REASSIGNED, actor.id(), AuditLogWriter.TARGET_TICKET, ticketId, before, after, ipAddress, reason);
	}

	/** The assignee, or a manager or an administrator, puts an {@code IN_PROGRESS} ticket back into the pool. */
	@Transactional
	public void release(CurrentUser actor, long ticketId, long expectedAssigneeId, String reason, String ipAddress) {
		Locked ticket = lockVisible(actor, ticketId);
		if (ticket.status() != TicketStatus.IN_PROGRESS || !Objects.equals(ticket.assigneeId(), expectedAssigneeId)) {
			throw changed(ticket.status());
		}
		if (expectedAssigneeId != actor.id() && !actor.can(Permission.TICKET_HANDLE_ANY)) {
			throw notAllowed("You can release only a ticket you hold");
		}
		if (repository.release(ticketId, expectedAssigneeId) == 0) {
			throw changed(ticket.status());
		}
		releaseRecord(actor.id(), actor.id(), ticket, expectedAssigneeId, reason, ipAddress);
	}

	/** The assignee, or a manager or an administrator, resolves an {@code IN_PROGRESS} ticket. */
	@Transactional
	public void resolve(CurrentUser actor, long ticketId, String resolution, String ipAddress) {
		Locked ticket = lockVisible(actor, ticketId);
		if (ticket.status() != TicketStatus.IN_PROGRESS) {
			throw changed(ticket.status());
		}
		if (!Objects.equals(ticket.assigneeId(), actor.id()) && !actor.can(Permission.TICKET_HANDLE_ANY)) {
			throw notAllowed("You can resolve only a ticket you hold");
		}
		if (repository.resolve(ticketId, resolution, actor.id(), now()) == 0) {
			throw changed(ticket.status());
		}
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", TicketStatus.RESOLVED.name());
		after.put("resolvedBy", actor.id());
		after.put("resolvedByUsername", actor.username());
		after.put("ticketNo", ticket.ticketNo());
		audit.write(AuditAction.TICKET_RESOLVED, actor.id(), AuditLogWriter.TARGET_TICKET, ticketId, Map.of("status", ticket.status().name()),
				after, ipAddress, null);
	}

	/**
	 * Cancels an {@code OPEN} or {@code IN_PROGRESS} ticket. An operator cancels only their own: the one they
	 * hold, or one they created while it is still {@code OPEN}.
	 */
	@Transactional
	public void cancel(CurrentUser actor, long ticketId, String reason, String ipAddress) {
		Locked ticket = lockVisible(actor, ticketId);
		if (!ticket.status().canMoveTo(TicketStatus.CANCELLED)) {
			throw changed(ticket.status());
		}
		boolean own = ticket.status() == TicketStatus.IN_PROGRESS ? Objects.equals(ticket.assigneeId(), actor.id())
				: ticket.createdBy() == actor.id();
		if (!own && !actor.can(Permission.TICKET_HANDLE_ANY)) {
			throw notAllowed("You can cancel only a ticket you hold, or one you opened while it waits in the pool");
		}
		if (repository.cancel(ticketId, reason, actor.id(), now()) == 0) {
			throw changed(ticket.status());
		}
		cancelRecord(actor.id(), actor.id(), actor.username(), ticket, reason, ipAddress);
	}

	/**
	 * Archives a {@code RESOLVED} ticket: {@code CLOSED}. A person archives as themselves; the archive job
	 * passes no actor and archives as the SYSTEM identity.
	 *
	 * @param actor the person, or null for the archive job
	 * @param batchId the id shared by the rows of one batch or one run of the job, or null for a single archive
	 * @return the number of the ticket
	 */
	@Transactional
	public String archive(CurrentUser actor, long ticketId, String batchId, String remark, String ipAddress) {
		Locked ticket = actor == null ? repository.lockTicket(ticketId).orElseThrow(TicketWriter::notFound) : lockVisible(actor, ticketId);
		long closedBy = actor == null ? systemUserId() : actor.id();
		if (ticket.status() != TicketStatus.RESOLVED || repository.close(ticketId, closedBy, now()) == 0) {
			throw changed(ticket.status());
		}
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", TicketStatus.CLOSED.name());
		after.put("ticketNo", ticket.ticketNo());
		after.put("closedBy", closedBy);
		after.put("closedByUsername", actor == null ? repository.username(closedBy) : actor.username());
		if (batchId != null) {
			after.put("batchId", batchId);
		}
		audit.write(AuditAction.TICKET_CLOSED, actor == null ? null : actor.id(), AuditLogWriter.TARGET_TICKET, ticketId,
				Map.of("status", TicketStatus.RESOLVED.name()), after, ipAddress, remark);
		return ticket.ticketNo();
	}

	// The steps that the cascades of the other modules share (see TicketCascades)

	/** Returns a ticket to the pool as the SYSTEM identity. Called with the ticket row locked. */
	boolean releaseBySystem(Locked ticket, String reason) {
		long assigneeId = ticket.assigneeId();
		if (repository.release(ticket.id(), assigneeId) == 0) {
			return false;
		}
		releaseRecord(systemUserId(), null, ticket, assigneeId, reason, null);
		return true;
	}

	/** Cancels a ticket as the SYSTEM identity. Called with the ticket row locked. */
	boolean cancelBySystem(Locked ticket, String reason) {
		long system = systemUserId();
		if (repository.cancel(ticket.id(), reason, system, now()) == 0) {
			return false;
		}
		cancelRecord(system, null, repository.username(system), ticket, reason, null);
		return true;
	}

	long systemUserId() {
		Long id = systemUserId;
		if (id == null) {
			id = repository.systemUserId();
			systemUserId = id;
		}
		return id;
	}

	// Helpers

	private void releaseRecord(long changedBy, Long operatorId, Locked ticket, long assigneeId, String reason, String ipAddress) {
		repository.insertHistory(ticket.id(), assigneeId, null, changedBy, reason, now());
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("status", TicketStatus.IN_PROGRESS.name());
		before.put("assigneeId", assigneeId);
		before.put("assigneeUsername", repository.username(assigneeId));
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", TicketStatus.OPEN.name());
		after.put("ticketNo", ticket.ticketNo());
		audit.write(AuditAction.TICKET_RELEASED, operatorId, AuditLogWriter.TARGET_TICKET, ticket.id(), before, after, ipAddress, reason);
	}

	private void cancelRecord(long cancelledBy, Long operatorId, String cancelledByUsername, Locked ticket, String reason, String ipAddress) {
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", TicketStatus.CANCELLED.name());
		after.put("cancelledBy", cancelledBy);
		after.put("cancelledByUsername", cancelledByUsername);
		after.put("ticketNo", ticket.ticketNo());
		audit.write(AuditAction.TICKET_CANCELLED, operatorId, AuditLogWriter.TARGET_TICKET, ticket.id(), Map.of("status", ticket.status().name()),
				after, ipAddress, reason);
	}

	private void logAssigned(long actorId, Locked ticket, UserLock assignee, String remark, String ipAddress) {
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("status", TicketStatus.IN_PROGRESS.name());
		after.put("assigneeId", assignee.id());
		after.put("assigneeUsername", assignee.username());
		after.put("ticketNo", ticket.ticketNo());
		audit.write(AuditAction.TICKET_ASSIGNED, actorId, AuditLogWriter.TARGET_TICKET, ticket.id(), Map.of("status", TicketStatus.OPEN.name()),
				after, ipAddress, remark);
	}

	/**
	 * Locks the rows of the actor and of the new assignee in ascending id, checks under those locks that the
	 * actor may still assign, and that the assignee is eligible for this actor (see Who may take a ticket).
	 *
	 * @return the assignee
	 */
	private UserLock lockActorAndAssignee(long actorId, long assigneeId) {
		UserLock first = repository.lockUser(Math.min(actorId, assigneeId)).orElse(null);
		UserLock second = actorId == assigneeId ? first : repository.lockUser(Math.max(actorId, assigneeId)).orElse(null);
		UserLock actor = actorId <= assigneeId ? first : second;
		UserLock assignee = actorId <= assigneeId ? second : first;
		requireUsable(actor);
		List<String> actorRoles = repository.roleCodes(actorId);
		if (!TicketAccess.grants(actorRoles, Permission.TICKET_ASSIGN)) {
			throw noRoleOrForbidden(actorRoles, "Your roles no longer allow you to assign tickets");
		}
		if (assignee == null || !assignee.usable()) {
			throw new ApiException(ErrorCode.NOT_ELIGIBLE, "This person cannot take tickets: the account is not usable");
		}
		List<String> assigneeRoles = repository.roleCodes(assigneeId);
		if (!TicketAccess.grants(assigneeRoles, Permission.TICKET_TAKE)) {
			throw new ApiException(ErrorCode.NOT_ELIGIBLE, assignee.username() + " has no role that may take tickets");
		}
		if (TicketAccess.grants(assigneeRoles, Permission.TICKET_ASSIGN_PROTECTED)
				&& !TicketAccess.grants(actorRoles, Permission.TICKET_ASSIGN_PROTECTED)) {
			throw new ApiException(ErrorCode.NOT_ELIGIBLE, "Only an administrator can give a ticket to an administrator");
		}
		return assignee;
	}

	/** Locks the actor's row and checks that the account is still usable, with the answers of the token check. */
	private UserLock usableActor(long actorId) {
		UserLock actor = repository.lockUser(actorId).orElse(null);
		requireUsable(actor);
		return actor;
	}

	/**
	 * The actor is still a usable account, read under the lock: the same answers as the token check gives on
	 * the next request of the account. The SYSTEM identity never sends a request.
	 */
	private static void requireUsable(UserLock actor) {
		if (actor == null || actor.deleted() || actor.system()) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		if (!actor.active()) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled");
		}
	}

	/** The in-progress limit of the person who would hold the ticket. Called with that person's row locked. */
	private void requireRoomInProgress(UserLock holder, String detail) {
		if (!holder.system() && repository.countInProgress(holder.id()) >= maxInProgress) {
			throw new ApiException(ErrorCode.LIMIT_REACHED, detail);
		}
	}

	/** Locks the ticket, and answers {@code NOT_FOUND} when it does not exist or the actor may not see it. */
	private Locked lockVisible(CurrentUser actor, long ticketId) {
		Locked ticket = repository.lockTicket(ticketId).orElseThrow(TicketWriter::notFound);
		if (!TicketAccess.visible(actor, ticket)) {
			throw notFound();
		}
		return ticket;
	}

	private static ApiException noRoleOrForbidden(List<String> roles, String detail) {
		if (roles.isEmpty()) {
			return new ApiException(ErrorCode.ACCOUNT_NO_ROLE, "This account is waiting for an administrator to assign permissions");
		}
		return new ApiException(ErrorCode.FORBIDDEN, detail);
	}

	static ApiException notFound() {
		return new ApiException(ErrorCode.NOT_FOUND, "There is no such ticket");
	}

	private static ApiException notAllowed(String detail) {
		return new ApiException(ErrorCode.NOT_ALLOWED, detail);
	}

	/** The ticket is not as the person saw it. The answer carries its status now, and the page reloads it. */
	static ApiException changed(TicketStatus status) {
		return new ApiException(ErrorCode.TICKET_CHANGED, "The ticket was changed by someone else, please reload").with("ticketStatus",
				status.name());
	}

	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}

}
