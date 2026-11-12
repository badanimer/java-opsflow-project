package com.opsflow.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request an id, so that a report from a user can be matched with the log lines of that
 * request (see docs/api/api-design.md).
 *
 * The id is made here, 32 hexadecimal characters. It is returned in the header {@value #HEADER}, written
 * on every log line of the request through the logging context ({@value #MDC_KEY}, see
 * {@code logging.pattern.correlation} in application.yml), and added to every error body by
 * {@code ApiExceptionHandler}. An id sent by the client is ignored: it would let a client write its own
 * text into the log.
 *
 * The filter runs before every other filter, the security filters included, so a request that is refused
 * early has an id too. The logging context belongs to the thread, and the thread serves another request
 * afterwards, so the id is removed when the request ends.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "requestId";

	private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

	/** The id of the request that the current thread is serving, or null outside a request. */
	public static String current() {
		return MDC.get(MDC_KEY);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String id = (String) request.getAttribute(ATTRIBUTE);
		if (id == null) {
			id = UUID.randomUUID().toString().replace("-", "");
			request.setAttribute(ATTRIBUTE, id);
		}
		response.setHeader(HEADER, id);
		MDC.put(MDC_KEY, id);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	/** The error dispatch of a failed request keeps the id of that request, read from the attribute above. */
	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

}
