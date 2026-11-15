package com.opsflow.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The answers of the security filters, which run before any controller. They are handed to the exception
 * handler of the controllers, so a refusal here has the same Problem Details body, with its {@code code}
 * and {@code requestId}, as any other error (see docs/api/api-design.md):
 *
 * <ul>
 * <li>No token, or a token that is not accepted: {@code UNAUTHENTICATED} (401).</li>
 * <li>A token of an earlier login, after the account logged in again: {@code SESSION_REPLACED} (401).</li>
 * <li>A token of a disabled account: {@code ACCOUNT_DISABLED} (403).</li>
 * <li>A user with no role on an endpoint that needs one: {@code ACCOUNT_NO_ROLE} (403).</li>
 * <li>A request that is not allowed for this user: {@code FORBIDDEN} (403).</li>
 * </ul>
 *
 * The reason a token was refused is not put in the body.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final HandlerExceptionResolver resolver;

	SecurityErrorHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure) {
		if (failure instanceof DisabledException) {
			resolver.resolveException(request, response, null,
					new ApiException(ErrorCode.ACCOUNT_DISABLED, LoginService.DISABLED_MESSAGE));
			return;
		}
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		if (failure instanceof TokenAuthenticator.SessionReplacedException) {
			resolver.resolveException(request, response, null,
					new ApiException(ErrorCode.SESSION_REPLACED, "This account signed in on another device"));
			return;
		}
		resolver.resolveException(request, response, null, new ApiException(ErrorCode.UNAUTHENTICATED, "Log in to continue"));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied) {
		if (denied instanceof EndpointPermissions.NoRoleException) {
			resolver.resolveException(request, response, null,
					new ApiException(ErrorCode.ACCOUNT_NO_ROLE, "This account is waiting for an administrator to assign permissions"));
			return;
		}
		resolver.resolveException(request, response, null,
				new ApiException(ErrorCode.FORBIDDEN, "You are not allowed to do this"));
	}

}
