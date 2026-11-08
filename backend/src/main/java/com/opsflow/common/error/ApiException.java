package com.opsflow.common.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A refusal that the API reports with a result code. Services throw it; the exception handler turns it
 * into the error body of docs/api/api-design.md.
 *
 * The detail is for people, so it must never hold a stack trace, an SQL message, a password, a code, or
 * a hash. Extra fields that belong to the result, such as {@code orderNo}, go in {@link #extras()} and
 * appear in the body next to {@code code}.
 */
public class ApiException extends RuntimeException {

	/** The names that every error body already has. */
	private static final Set<String> RESERVED = Set.of("type", "title", "status", "detail", "instance", "code", "errors");

	private final ErrorCode code;
	private final transient Map<String, Object> extras = new LinkedHashMap<>();

	public ApiException(ErrorCode code) {
		this(code, null, null);
	}

	public ApiException(ErrorCode code, String detail) {
		this(code, detail, null);
	}

	public ApiException(ErrorCode code, Throwable cause) {
		this(code, null, cause);
	}

	public ApiException(ErrorCode code, String detail, Throwable cause) {
		super(detail != null ? detail : code.title(), cause);
		this.code = code;
	}

	/** A {@code VALIDATION_FAILED} that names the one field that was refused. */
	public static ApiException invalid(String field, String message) {
		ApiException e = new ApiException(ErrorCode.VALIDATION_FAILED, "Request validation failed");
		e.extras.put("errors", List.of(Map.of("field", field, "message", message)));
		return e;
	}

	/** Adds a field to the error body. A name that the body already uses ({@code code}, {@code status}, ...) is refused. */
	public ApiException with(String name, Object value) {
		if (RESERVED.contains(name)) {
			throw new IllegalArgumentException("'" + name + "' is a standard field of the error body");
		}
		extras.put(name, value);
		return this;
	}

	public ErrorCode code() {
		return code;
	}

	public Map<String, Object> extras() {
		return Map.copyOf(extras);
	}

}
