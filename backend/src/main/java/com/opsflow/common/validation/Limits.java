package com.opsflow.common.validation;

/**
 * The length limit of every text field that a request can set, equal to the length of its database
 * column (T-13), so that over-long input is refused with {@code VALIDATION_FAILED} and never truncated.
 *
 * Use the constants in the annotations of a request type: {@code @Size(max = Limits.ITEM_NAME)}. They
 * are plain {@code int} constants, which is what an annotation needs. {@code LimitsMatchSchemaTest}
 * compares each one with the column in the database, so a migration that changes a length fails the
 * build until the constant follows. Add a constant here, and a line in that test, when a request type
 * gets a new text field.
 *
 * A {@code TEXT} column has no length of its own, so its limit is a decision of the design (Field
 * Specifications in database-design.md): the test checks only that the limit fits the column, at four
 * bytes a character.
 */
public final class Limits {

	/** {@code users.username} */
	public static final int USERNAME = 64;
	/** {@code users.email}, and {@code email_verification_codes.email} */
	public static final int EMAIL = 254;
	/** {@code roles.code} */
	public static final int ROLE_CODE = 32;
	/** {@code roles.name} */
	public static final int ROLE_NAME = 64;
	/** {@code roles.description} */
	public static final int ROLE_DESCRIPTION = 255;
	/** {@code inventory.sku} */
	public static final int SKU = 8;
	/** {@code inventory.name}, {@code products.name}, and the name copied into other tables */
	public static final int ITEM_NAME = 200;
	/** {@code orders.order_no}, and {@code tickets.order_no} */
	public static final int ORDER_NO = 64;
	/** {@code orders.idempotency_key} */
	public static final int IDEMPOTENCY_KEY = 128;
	/** {@code orders.remark} */
	public static final int ORDER_REMARK = 500;
	/** {@code tickets.ticket_no} */
	public static final int TICKET_NO = 8;
	/** {@code tickets.title} */
	public static final int TICKET_TITLE = 200;
	/** A reason typed by a person: {@code inventory_transactions.reason}, {@code tickets.cancel_reason}, and the reasons of the ticket histories */
	public static final int REASON = 255;
	/** {@code tickets.description}, a {@code TEXT} column: the limit is the design's, not the column's */
	public static final int TICKET_DESCRIPTION = 4000;
	/** {@code tickets.resolution}, a {@code TEXT} column: the limit is the design's, not the column's */
	public static final int TICKET_RESOLUTION = 2000;

	private Limits() {
	}

}
