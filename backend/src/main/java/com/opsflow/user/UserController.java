package com.opsflow.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageQueryFactory;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.paging.SortOrder;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.RequiresPermission;
import com.opsflow.common.validation.Limits;
import com.opsflow.user.UserChangeWriter.UserRoles;
import com.opsflow.user.UserChangeWriter.UserStatus;
import com.opsflow.user.UserRepository.ListedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The endpoints of the user page. See Users in docs/api/api-design.md. */
@RestController
@RequestMapping("/users")
public class UserController {

	/** More entries than any list of roles can have, so that a request cannot send a list without end. */
	private static final int MAX_ROLES = 32;

	/**
	 * @param roles the role codes the user is to hold afterwards; an empty list leaves the user with no role
	 * @param expectedRoles the role codes the user held on the screen of the person who sends the request
	 */
	public record ReplaceRolesRequest(
			@NotNull @Size(max = MAX_ROLES) List<@NotBlank @Size(max = Limits.ROLE_CODE) String> roles,
			@NotNull @Size(max = MAX_ROLES) List<@NotBlank @Size(max = Limits.ROLE_CODE) String> expectedRoles) {
	}

	/** @param ticketsReturned the tickets in progress that went back to the pool with this change */
	public record UserRolesResponse(long id, String username, List<String> roles, int ticketsReturned) {
	}

	/** @param ticketsReturned the tickets in progress that the change would return to the pool */
	public record RoleChangeResponse(int ticketsReturned) {
	}

	/** @param status {@code ACTIVE} or {@code DISABLED} */
	public record UserStatusResponse(long id, String username, String status, int ticketsReturned) {
	}

	/**
	 * A row of the user page.
	 *
	 * @param status {@code ACTIVE} or {@code DISABLED}
	 * @param roles the role codes, in the order of the codes; empty for a user who waits for a role
	 */
	public record UserListItem(long id, String username, String status, List<String> roles, int ticketsInProgress) {
	}

	private final UserService users;
	private final PageQueryFactory pages;

	UserController(UserService users, PageQueryFactory pages) {
		this.users = users;
		this.pages = pages;
	}

	/**
	 * The user page: the users the caller may manage, from A to Z by username, paged by key.
	 *
	 * @param search a part of the username, matched without regard to case
	 * @param roles the role buttons, as role codes and {@code NONE}, comma-separated or repeated
	 */
	@RequiresPermission(Permission.USER_VIEW)
	@GetMapping
	public PageResponse<UserListItem> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) List<String> roles, @RequestParam(required = false) List<String> status,
			@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) String direction,
			@RequestParam(required = false) String order, @AuthenticationPrincipal CurrentUser user) {
		PageQuery page = pages.createInOrder(limit, cursor, direction, order, SortOrder.ASC);
		PageResponse<ListedUser> found = users.list(user, search, roles == null ? List.of() : roles, status == null ? List.of() : status, page);
		List<UserListItem> items = found.items().stream()
				.map(row -> new UserListItem(row.id(), row.username(), row.status(), row.roles(), row.ticketsInProgress())).toList();
		return new PageResponse<>(items, found.nextCursor(), found.prevCursor());
	}

	/**
	 * The permission opens the endpoint. Whether this user may change that user, and to these roles, is
	 * decided in the transaction, from the roles both hold then.
	 */
	@RequiresPermission(Permission.ROLE_ASSIGN)
	@PutMapping("/{id}/roles")
	public UserRolesResponse replaceRoles(@PathVariable long id, @Valid @RequestBody ReplaceRolesRequest body,
			@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		UserRoles result = users.replaceRoles(user, id, body.roles(), body.expectedRoles(), request.getRemoteAddr());
		return new UserRolesResponse(result.id(), result.username(), result.roles(), result.ticketsReturned());
	}

	/**
	 * What a change of the roles to this list would do to the tickets of the user, for the Change roles
	 * dialog. It changes nothing.
	 */
	@RequiresPermission(Permission.ROLE_ASSIGN)
	@GetMapping("/{id}/role-change")
	public RoleChangeResponse roleChange(@PathVariable long id, @RequestParam(required = false) List<String> roles,
			@AuthenticationPrincipal CurrentUser user) {
		return new RoleChangeResponse(users.ticketsReturnedBy(user, id, roles == null ? List.of() : roles));
	}

	@RequiresPermission(Permission.USER_MANAGE)
	@PostMapping("/{id}/disable")
	public UserStatusResponse disable(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		UserStatus result = users.disable(user, id, request.getRemoteAddr());
		return new UserStatusResponse(result.id(), result.username(), result.status(), result.ticketsReturned());
	}

	@RequiresPermission(Permission.USER_MANAGE)
	@PostMapping("/{id}/enable")
	public UserStatusResponse enable(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		UserStatus result = users.enable(user, id, request.getRemoteAddr());
		return new UserStatusResponse(result.id(), result.username(), result.status(), result.ticketsReturned());
	}

	/** A soft deletion: the account is marked as removed, and its row stays. */
	@RequiresPermission(Permission.USER_MANAGE)
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void softDelete(@PathVariable long id, @AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
		users.softDelete(user, id, request.getRemoteAddr());
	}

}
