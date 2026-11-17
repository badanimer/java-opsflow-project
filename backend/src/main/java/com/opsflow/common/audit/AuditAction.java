package com.opsflow.common.audit;

/**
 * The one table of the actions that are written to {@code operation_logs}, each with the category it is
 * filed under (see Categories in docs/database/database-design.md). A row can only be written through
 * {@link AuditLogWriter} with an action of this table, so no row is written without a category. A module
 * that logs a new action adds it here and to the design document.
 */
public enum AuditAction {

	USER_REGISTERED(AuditCategory.ACCOUNT, "Account created"),
	USER_STATUS_CHANGED(AuditCategory.ACCOUNT, "Changed on the user page"),
	USER_DELETED(AuditCategory.ACCOUNT, "Deleted on the user page"),
	PASSWORD_RESET(AuditCategory.ACCOUNT, "Reset with an email code"),
	LOGIN_LOCKED(AuditCategory.ACCOUNT, "Locked after failed logins"),

	ROLE_GRANTED(AuditCategory.ROLE, "Granted on the user page"),
	ROLE_REVOKED(AuditCategory.ROLE, "Revoked on the user page"),

	TICKET_CREATED(AuditCategory.TICKET, "Opened on the ticket page"),
	TICKET_ASSIGNED(AuditCategory.TICKET, "Assigned"),
	TICKET_REASSIGNED(AuditCategory.TICKET, "Reassigned"),
	TICKET_RELEASED(AuditCategory.TICKET, "Released"),
	TICKET_RESOLVED(AuditCategory.TICKET, "Resolved, with the resolution kept on the ticket"),
	TICKET_CLOSED(AuditCategory.TICKET, "Archived"),
	TICKET_CANCELLED(AuditCategory.TICKET, "Cancelled"),

	STOCK_RESTOCK(AuditCategory.STOCK, "Stock in"),
	STOCK_WRITE_OFF(AuditCategory.STOCK, "Write-off"),
	STOCK_ADJUSTED(AuditCategory.STOCK, "Correction"),

	ITEM_CREATED(AuditCategory.ITEM, "Added on the stock page"),
	ITEM_UPDATED(AuditCategory.ITEM, "Edited on the stock page"),
	ITEM_DELETED(AuditCategory.ITEM, "Deleted on the stock page"),

	PRODUCT_LISTED(AuditCategory.PRODUCT, "Listed on the product page"),
	PRODUCT_DELISTED(AuditCategory.PRODUCT, "Delisted on the product page");

	private final AuditCategory category;
	private final String fixedRemark;

	AuditAction(AuditCategory category, String fixedRemark) {
		this.category = category;
		this.fixedRemark = fixedRemark;
	}

	/**
	 * What the row says when the change gave no reason of its own (T-69): a row is never written with an
	 * empty remark. A reason that a person typed, or that the system wrote for its own action, takes its place.
	 */
	public String fixedRemark() {
		return fixedRemark;
	}

	public AuditCategory category() {
		return category;
	}

}
