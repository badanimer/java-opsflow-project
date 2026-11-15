package com.opsflow.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.opsflow.auth.AccountService.NewAccount;
import com.opsflow.auth.TokenService.IssuedToken;
import com.opsflow.common.security.AnyAccount;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.Public;
import com.opsflow.common.validation.Limits;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Login, the account of the caller, the email codes, sign-up, and the password reset. See Authentication
 * in docs/api/api-design.md.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

	/** A password has at most 24 characters (the password rule), so a longer one cannot be right and is not hashed. */
	static final int PASSWORD_MAX = AccountRules.PASSWORD_MAX;

	/** An email code has 6 digits. */
	private static final String CODE = "\\d{6}";

	public record LoginRequest(@NotBlank @Size(max = Limits.USERNAME) String username,
			@NotBlank @Size(max = PASSWORD_MAX) String password) {
	}

	public record LoginResponse(String token, Instant expiresAt) {
	}

	/**
	 * @param roles the role codes; empty while the account waits for an administrator to assign one
	 * @param permissions what the user may do, as the names of {@link Permission}: the permissions of all
	 *        the roles together. The frontend shows a control by its permission and never by a role, so
	 *        that a new role needs no change there. It only decides what is shown: every request is
	 *        checked again by the server
	 */
	public record AccountResponse(long id, String username, List<String> roles, List<Permission> permissions) {
	}

	public record EmailCodeRequest(@NotBlank @Email @Size(max = Limits.EMAIL) String email, @NotNull EmailCodePurpose purpose) {
	}

	public record SignUpRequest(@NotBlank @Size(max = Limits.USERNAME) String username,
			@NotBlank @Email @Size(max = Limits.EMAIL) String email, @NotBlank @Size(max = PASSWORD_MAX) String password,
			@NotBlank @Pattern(regexp = CODE, message = "must be 6 digits") String code) {
	}

	/** The new account is logged in: the token takes the user to the pending page, where the account waits for a role. */
	public record SignUpResponse(long id, String username, String token, Instant expiresAt) {
	}

	public record PasswordResetRequest(@NotBlank @Email @Size(max = Limits.EMAIL) String email,
			@NotBlank @Pattern(regexp = CODE, message = "must be 6 digits") String code,
			@NotBlank @Size(max = PASSWORD_MAX) String newPassword) {
	}

	private final LoginService loginService;
	private final EmailCodeService emailCodes;
	private final AccountService accounts;
	private final SessionEvents sessionEvents;

	AuthController(LoginService loginService, EmailCodeService emailCodes, AccountService accounts, SessionEvents sessionEvents) {
		this.loginService = loginService;
		this.emailCodes = emailCodes;
		this.accounts = accounts;
		this.sessionEvents = sessionEvents;
	}

	@Public
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
		IssuedToken token = loginService.login(body.username(), body.password(), request.getRemoteAddr());
		return new LoginResponse(token.value(), token.expiresAt());
	}

	@AnyAccount
	@GetMapping("/me")
	public AccountResponse me(@AuthenticationPrincipal CurrentUser user) {
		return new AccountResponse(user.id(), user.username(), user.roles(), List.copyOf(user.permissions()));
	}

	/**
	 * The event stream of a signed-in page: it gets {@code session-replaced} as soon as the account signs
	 * in on another device, and then ends. Open to a user with no role too, because the pending page is
	 * signed in as well. See {@link SessionEvents}.
	 */
	@AnyAccount
	@GetMapping(path = "/session-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter sessionEvents(Authentication authentication) {
		TokenAuthenticator.CurrentUserAuthentication current = (TokenAuthenticator.CurrentUserAuthentication) authentication;
		return sessionEvents.open(current.getPrincipal().id(), current.sessionId());
	}

	/** The answer is the same whether or not a mail is sent, so it does not show which addresses are registered. */
	@Public
	@PostMapping("/email-codes")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void requestEmailCode(@Valid @RequestBody EmailCodeRequest body, HttpServletRequest request) {
		emailCodes.request(body.email(), body.purpose(), request.getRemoteAddr());
	}

	@Public
	@PostMapping("/sign-up")
	@ResponseStatus(HttpStatus.CREATED)
	public SignUpResponse signUp(@Valid @RequestBody SignUpRequest body, HttpServletRequest request) {
		NewAccount account = accounts.signUp(body.username(), body.email(), body.password(), body.code(), request.getRemoteAddr());
		return new SignUpResponse(account.id(), account.username(), account.token().value(), account.token().expiresAt());
	}

	@Public
	@PostMapping("/password-reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
		accounts.resetPassword(body.email(), body.code(), body.newPassword(), request.getRemoteAddr());
	}

}
