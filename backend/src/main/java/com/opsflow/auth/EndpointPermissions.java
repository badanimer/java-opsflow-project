package com.opsflow.auth;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import com.opsflow.common.security.AnyAccount;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Public;
import com.opsflow.common.security.RequiresPermission;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decides whether a request may reach its controller method, from what that method declares (T-27; see
 * Permission Matrix in docs/database/database-design.md).
 *
 * <ul>
 * <li>{@link Public}: everyone.</li>
 * <li>{@link AnyAccount}: every logged-in user, with or without a role.</li>
 * <li>{@link RequiresPermission}: a logged-in user with a role that grants the permission. A user with
 * no role at all is answered {@code ACCOUNT_NO_ROLE} (T-22), and a user whose roles do not grant it
 * {@code FORBIDDEN}. Which role grants what is written in {@link com.opsflow.common.security.Role}.</li>
 * <li>No declaration, or no controller method for the request: refused. An endpoint that somebody
 * forgot to declare is therefore closed and not open.</li>
 * </ul>
 *
 * A request without a token that is refused here is answered {@code UNAUTHENTICATED}, by the filters.
 *
 * The roles are the ones {@link TokenAuthenticator} read from the database for this request, and only a
 * usable account gets this far, so a role counts only for a usable account (T-19).
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class EndpointPermissions implements AuthorizationManager<RequestAuthorizationContext> {

	/** A logged-in user with no role called an endpoint that needs one. */
	static final class NoRoleException extends AccessDeniedException {

		private static final long serialVersionUID = 1L;

		NoRoleException() {
			super("The account has no role");
		}
	}

	private static final AuthorizationDecision ALLOW = new AuthorizationDecision(true);
	private static final AuthorizationDecision REFUSE = new AuthorizationDecision(false);

	private final RequestMappingHandlerMapping endpoints;

	EndpointPermissions(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
		HandlerMethod endpoint = endpointOf(context.getRequest());
		if (endpoint == null) {
			return REFUSE;
		}
		if (endpoint.hasMethodAnnotation(Public.class)) {
			return ALLOW;
		}
		Authentication caller = authentication.get();
		if (caller == null || !(caller.getPrincipal() instanceof CurrentUser user)) {
			return REFUSE;
		}
		if (endpoint.hasMethodAnnotation(AnyAccount.class)) {
			return ALLOW;
		}
		RequiresPermission required = endpoint.getMethodAnnotation(RequiresPermission.class);
		if (required == null) {
			return REFUSE;
		}
		if (user.roles().isEmpty()) {
			throw new NoRoleException();
		}
		return user.can(required.value()) ? ALLOW : REFUSE;
	}

	/** The controller method that would answer this request, or null when there is none. */
	private HandlerMethod endpointOf(HttpServletRequest request) {
		try {
			if (!ServletRequestPathUtils.hasParsedRequestPath(request)) {
				ServletRequestPathUtils.parseAndCache(request);
			}
			HandlerExecutionChain chain = endpoints.getHandler(request);
			return chain != null && chain.getHandler() instanceof HandlerMethod method ? method : null;
		}
		catch (Exception noSuchEndpoint) {
			// A path that exists with another method, or with another content type, has no endpoint for this request.
			return null;
		}
	}

}
