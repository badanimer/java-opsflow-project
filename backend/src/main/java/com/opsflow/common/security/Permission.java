package com.opsflow.common.security;

/**
 * What a user may do: one constant for each action of the Permission Matrix in
 * docs/database/database-design.md that some roles may do and others may not.
 *
 * The code never asks which role a user holds to decide whether an action is allowed. An endpoint names
 * the permission it needs with {@link RequiresPermission}, a service asks {@link CurrentUser#can}, and
 * {@link Role} is the one place that says which role grants which permission. A new role is therefore
 * one constant in {@link Role}, and no endpoint changes.
 *
 * A permission opens the action. A rule that depends on the data, such as an operator who may resolve
 * only a ticket assigned to them, is checked by the service, inside the transaction of the change.
 * Where a wider reach is itself a row of the matrix, it is a permission of its own, such as
 * {@link #TICKET_VIEW_ALL}.
 *
 * The actions that no role may do (reopening a ticket, editing an audit log, creating a role, changing
 * one's own account) have no permission: there is no endpoint for them, or the service refuses them for
 * everyone. Changing the password needs no login, so it has none either.
 */
public enum Permission {

	// Products

	/** View the product page, search it, and read the product log. */
	PRODUCT_VIEW,
	/** List a warehouse item as a product, with its price. */
	PRODUCT_LIST,
	/** Delist a product. */
	PRODUCT_DELIST,

	// Inventory

	/** Add an item to the warehouse, and edit the name or image of an item that is not listed. */
	ITEM_MANAGE,
	/** Delete an item softly. */
	ITEM_DELETE,
	/** View the stock page, search it, and read the warehouse log. */
	STOCK_VIEW,
	/** Search the Deleted items module of the warehouse log. */
	DELETED_ITEMS_VIEW,
	/** Stock in ({@code RESTOCK}). */
	STOCK_IN,
	/** Write-off or stock out ({@code WRITE_OFF}). */
	STOCK_WRITE_OFF,
	/** Correction to the quantity that was counted ({@code ADJUST_IN}, {@code ADJUST_OUT}). */
	STOCK_CORRECT,

	// Orders

	/** Place an order from the product page. */
	ORDER_PLACE,

	// Tickets

	/** Open the ticket screens. Without {@link #TICKET_VIEW_ALL} they show the pool and the user's own tickets. */
	TICKET_VIEW,
	/** See everyone's tickets on every screen, the archive screen, the follow-up links, and the assignment history. */
	TICKET_VIEW_ALL,
	/** Search and filter the ticket screens, and read the usernames for the creator and assignee filters. */
	TICKET_SEARCH,
	/** Create a ticket, by hand or as a follow-up. */
	TICKET_CREATE,
	/** Take a ticket from the pool. A user with this permission is an eligible assignee. */
	TICKET_TAKE,
	/** Assign a ticket from the pool, or reassign one that is in progress. */
	TICKET_ASSIGN,
	/** Release a ticket back to the pool. */
	TICKET_RELEASE,
	/** Move a ticket to {@code RESOLVED}. */
	TICKET_RESOLVE,
	/** Cancel an open or in-progress ticket. */
	TICKET_CANCEL,
	/** Archive a resolved ticket. */
	TICKET_ARCHIVE,

	// Audit logs

	/** Open the audit page and filter it. */
	AUDIT_VIEW,

	// Users and roles

	/** View the user page. Which users it lists is decided by Authority over roles and accounts. */
	USER_VIEW,
	/** Disable, enable, or soft-delete another user who holds no protected role. */
	USER_MANAGE,
	/** Disable, enable, or soft-delete a user who holds a protected role too ({@link Role#isProtected}). */
	USER_MANAGE_PROTECTED,
	/** Grant and revoke the roles that are not protected, on a user who holds no protected role. */
	ROLE_ASSIGN,
	/**
	 * Grant and revoke the protected roles too ({@link Role#isProtected}), and change the roles of a user
	 * who holds one. See Authority over roles and accounts.
	 */
	ROLE_ASSIGN_PROTECTED

}
