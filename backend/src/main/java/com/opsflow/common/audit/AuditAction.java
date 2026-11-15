package com.opsflow.common.audit;

/**
 * The one table of the actions that are written to {@code operation_logs}, each with the category it is
 * filed under (see Categories in docs/database/database-design.md). A row can only be written through
 * {@link AuditLogWriter} with an action of this table, so no row is written without a category. A module
 * that logs a new action adds it here and to the design document.
 */
public enum AuditAction {

	USER_REGISTERED(AuditCategory.ACCOUNT),
	USER_STATUS_CHANGED(AuditCategory.ACCOUNT),
	USER_DELETED(AuditCategory.ACCOUNT),
	PASSWORD_RESET(AuditCategory.ACCOUNT),
	LOGIN_LOCKED(AuditCategory.ACCOUNT),

	ROLE_GRANTED(AuditCategory.ROLE),
	ROLE_REVOKED(AuditCategory.ROLE),

	TICKET_CREATED(AuditCategory.TICKET),
	TICKET_ASSIGNED(AuditCategory.TICKET),
	TICKET_REASSIGNED(AuditCategory.TICKET),
	TICKET_RELEASED(AuditCategory.TICKET),
	TICKET_RESOLVED(AuditCategory.TICKET),
	TICKET_CLOSED(AuditCategory.TICKET),
	TICKET_CANCELLED(AuditCategory.TICKET),

	STOCK_RESTOCK(AuditCategory.STOCK),
	STOCK_WRITE_OFF(AuditCategory.STOCK),
	STOCK_ADJUSTED(AuditCategory.STOCK),

	ITEM_CREATED(AuditCategory.ITEM),
	ITEM_UPDATED(AuditCategory.ITEM),
	ITEM_DELETED(AuditCategory.ITEM),

	PRODUCT_LISTED(AuditCategory.PRODUCT),
	PRODUCT_DELISTED(AuditCategory.PRODUCT);

	private final AuditCategory category;

	AuditAction(AuditCategory category) {
		this.category = category;
	}

	public AuditCategory category() {
		return category;
	}

}
