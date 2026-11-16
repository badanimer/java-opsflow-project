package com.opsflow.ticket;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.ticket.TicketRepository.Locked;

/**
 * The ticket steps that another module's change starts, inside that change's transaction (T-21, T-39; see
 * When an assignee's account stops being usable, Listing and delisting, and Warehouse items in
 * docs/database/database-design.md). The SYSTEM identity acts: it is {@code changed_by} or
 * {@code cancelled_by}, and the log rows have no operator.
 *
 * Each method needs the transaction of its caller, and the caller has already locked what comes before
 * {@code tickets} in the lock order: the user row, or the product and item rows. Each ticket is locked and
 * then moved through the status gate, so a ticket that someone resolved, released, or cancelled a moment
 * earlier is skipped.
 */
@Component
public class TicketCascades {

	/** The reason of the history row when the assignee's account was disabled. */
	public static final String ASSIGNEE_DISABLED = "The assignee's account was disabled";
	/** The reason of the history row when the assignee's account was deleted. */
	public static final String ASSIGNEE_DELETED = "The assignee's account was deleted";

	private final TicketRepository repository;
	private final TicketWriter writer;

	TicketCascades(TicketRepository repository, TicketWriter writer) {
		this.repository = repository;
		this.writer = writer;
	}

	/**
	 * Returns every {@code IN_PROGRESS} ticket of the user to the pool. Called with the user row locked, when
	 * the user is disabled or soft-deleted. The {@code OPEN} tickets the user created stay as they are.
	 *
	 * @param reason {@link #ASSIGNEE_DISABLED} or {@link #ASSIGNEE_DELETED}
	 * @return the ids of the tickets returned
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Long> returnToPool(long userId, String reason) {
		List<Long> returned = new ArrayList<>();
		for (Locked ticket : repository.lockInProgressOf(userId)) {
			if (writer.releaseBySystem(ticket, reason)) {
				returned.add(ticket.id());
			}
		}
		return returned;
	}

	/**
	 * Cancels every {@code OPEN} or {@code IN_PROGRESS} ticket linked to the item. Called after the item is
	 * locked for its deletion (reason {@code ITEM_DELETED:} and the item id), or after its product is locked
	 * for its delisting (reason {@code PRODUCT_DELISTED:} and the product id).
	 *
	 * @return the ids of the tickets cancelled
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Long> cancelLinkedTo(long inventoryId, String reason) {
		List<Long> cancelled = new ArrayList<>();
		for (Locked ticket : repository.lockLiveLinkedTo(inventoryId)) {
			if (writer.cancelBySystem(ticket, reason)) {
				cancelled.add(ticket.id());
			}
		}
		return cancelled;
	}

}
