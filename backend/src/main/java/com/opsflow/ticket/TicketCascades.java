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

	/**
	 * The tickets a cascade changed, in ascending id, with their numbers as labels for the log row of the
	 * change that started it (see Labels in audit rows).
	 */
	public record Changed(List<Long> ids, List<String> ticketNos) {
	}

	/** The reason of the history row when the assignee's account was disabled. */
	public static final String ASSIGNEE_DISABLED = "The assignee's account was disabled";
	/** The reason of the history row when the assignee's account was deleted. */
	public static final String ASSIGNEE_DELETED = "The assignee's account was deleted";
	/** The reason of the history row when the assignee lost every role that may take tickets. */
	public static final String ASSIGNEE_NO_TAKING_ROLE = "The assignee no longer has a role that may take tickets";

	private final TicketRepository repository;
	private final TicketWriter writer;

	TicketCascades(TicketRepository repository, TicketWriter writer) {
		this.repository = repository;
		this.writer = writer;
	}

	/**
	 * Returns every {@code IN_PROGRESS} ticket of the user to the pool. Called with the user row locked, when
	 * the user is disabled or soft-deleted, or loses every role that may take tickets. The {@code OPEN}
	 * tickets the user created stay as they are.
	 *
	 * @param reason {@link #ASSIGNEE_DISABLED}, {@link #ASSIGNEE_DELETED}, or {@link #ASSIGNEE_NO_TAKING_ROLE}
	 * @return the tickets returned
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Changed returnToPool(long userId, String reason) {
		List<Long> ids = new ArrayList<>();
		List<String> numbers = new ArrayList<>();
		for (Locked ticket : repository.lockInProgressOf(userId)) {
			if (writer.releaseBySystem(ticket, reason)) {
				ids.add(ticket.id());
				numbers.add(ticket.ticketNo());
			}
		}
		return new Changed(List.copyOf(ids), List.copyOf(numbers));
	}

	/**
	 * Cancels every {@code OPEN} or {@code IN_PROGRESS} ticket linked to the item. Called after the item is
	 * locked for its deletion (reason {@code ITEM_DELETED:} and the item id), or after its product is locked
	 * for its delisting (reason {@code PRODUCT_DELISTED:} and the product id).
	 *
	 * @return the tickets cancelled
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Changed cancelLinkedTo(long inventoryId, String reason) {
		List<Long> ids = new ArrayList<>();
		List<String> numbers = new ArrayList<>();
		for (Locked ticket : repository.lockLiveLinkedTo(inventoryId)) {
			if (writer.cancelBySystem(ticket, reason)) {
				ids.add(ticket.id());
				numbers.add(ticket.ticketNo());
			}
		}
		return new Changed(List.copyOf(ids), List.copyOf(numbers));
	}

}
