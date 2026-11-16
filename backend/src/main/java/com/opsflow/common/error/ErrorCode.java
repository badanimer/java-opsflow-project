package com.opsflow.common.error;

import org.springframework.http.HttpStatus;

/**
 * The result codes of the API. The names, statuses, and titles are the table in docs/api/api-design.md:
 * a code added here is added there in the same change. The frontend decides from {@code name()}, never
 * from the title or the detail, which are for people and may change.
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
	INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "Invalid quantity"),
	CODE_INVALID(HttpStatus.BAD_REQUEST, "Invalid code"),
	IMAGE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Image type not allowed"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
	SESSION_REPLACED(HttpStatus.UNAUTHORIZED, "Signed in elsewhere"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
	NOT_ALLOWED(HttpStatus.FORBIDDEN, "Not allowed"),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account disabled"),
	ACCOUNT_NO_ROLE(HttpStatus.FORBIDDEN, "Account has no role"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
	FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Ticket to follow not found"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
	IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Image too large"),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
	OUT_OF_STOCK(HttpStatus.CONFLICT, "Out of stock"),
	NO_STOCK(HttpStatus.CONFLICT, "No stock"),
	ALREADY_LISTED(HttpStatus.CONFLICT, "Already listed"),
	ALREADY_DELISTED(HttpStatus.CONFLICT, "Already delisted"),
	ITEM_NAME_TAKEN(HttpStatus.CONFLICT, "Item name taken"),
	ITEM_LISTED(HttpStatus.CONFLICT, "Item listed"),
	PRODUCT_DELISTED(HttpStatus.CONFLICT, "Product delisted"),
	PRODUCT_CHANGED(HttpStatus.CONFLICT, "Product changed"),
	ITEM_CHANGED(HttpStatus.CONFLICT, "Item changed"),
	STOCK_CHANGED(HttpStatus.CONFLICT, "Stock changed"),
	FOLLOW_NOT_ALLOWED(HttpStatus.CONFLICT, "Ticket cannot be followed"),
	LIMIT_REACHED(HttpStatus.CONFLICT, "Limit reached"),
	TICKET_CHANGED(HttpStatus.CONFLICT, "Ticket changed"),
	NOT_ELIGIBLE(HttpStatus.CONFLICT, "Assignee not eligible"),
	USER_CHANGED(HttpStatus.CONFLICT, "User changed"),
	USER_NOT_ACTIVE(HttpStatus.CONFLICT, "User not active"),
	LAST_ADMIN(HttpStatus.CONFLICT, "Last administrator"),
	USERNAME_TAKEN(HttpStatus.CONFLICT, "Username taken"),
	EMAIL_TAKEN(HttpStatus.CONFLICT, "Email taken"),
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Login locked"),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
	BUSY(HttpStatus.SERVICE_UNAVAILABLE, "Busy"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

	private final HttpStatus status;
	private final String title;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}

}
