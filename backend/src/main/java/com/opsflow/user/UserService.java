package com.opsflow.user;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.paging.Cursor;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.retry.TransactionRetry;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.Role;
import com.opsflow.common.validation.Limits;
import com.opsflow.user.UserChangeWriter.UserRoles;
import com.opsflow.user.UserChangeWriter.UserStatus;
import com.opsflow.user.UserRepository.ListFilter;
import com.opsflow.user.UserRepository.ListedUser;
import com.opsflow.user.UserRepository.UserOption;

/**
 * The user page and the changes one user makes to another (see User page and Authority over roles and
 * accounts in docs/database/database-design.md). This class has no transaction: for a change it checks
 * what needs no database, and then runs a short transaction of {@link UserChangeWriter} through
 * {@link TransactionRetry}.
 *
 * The roles of the actor that the request arrived with are not used here. The transaction locks the
 * row of the actor and reads the roles again, so an actor who was demoted a moment ago is refused.
 */
@Service
public class UserService {

	/** The value of the role filter that stands for the button No role: the users who hold no role. */
	public static final String NO_ROLE = "NONE";

	/** The values of the status buttons of the user page. A deleted user is listed only with {@code DELETED}. */
	private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED", "DELETED");

	private final UserChangeWriter writer;
	private final UserRepository repository;
	private final TransactionRetry retry;

	UserService(UserChangeWriter writer, UserRepository repository, TransactionRetry retry) {
		this.writer = writer;
		this.repository = repository;
		this.retry = retry;
	}

	/**
	 * A page of the user page: the users the viewer may manage (see User page in
	 * docs/database/database-design.md), narrowed by the search and the role buttons, ordered by username.
	 *
	 * A viewer who may change users who hold a protected role sees every user, their own account included,
	 * which the page shows with no control. Any other viewer sees only the users who hold no protected
	 * role, and never their own account. Neither filter widens this.
	 *
	 * @param search a part of the username, or null or blank for any
	 * @param roleCodes the role buttons: role codes, and {@link #NO_ROLE}; empty for no button
	 * @param statusCodes the status buttons: {@code ACTIVE}, {@code DISABLED}, {@code DELETED}; empty for the users who are not deleted
	 * @param page the paging, in the one order of this list, from A to Z
	 */
	public PageResponse<ListedUser> list(CurrentUser viewer, String search, List<String> roleCodes, List<String> statusCodes,
			PageQuery page) {
		boolean seesProtected = viewer.can(Permission.USER_MANAGE_PROTECTED) || viewer.can(Permission.ROLE_ASSIGN_PROTECTED);
		List<String> hiddenRoles = seesProtected ? List.of()
				: Arrays.stream(Role.values()).filter(Role::isProtected).map(Role::code).toList();
		Long hiddenUserId = seesProtected ? null : viewer.id();

		String part = search == null || search.isBlank() ? null : search.strip();
		if (part != null && part.length() > Limits.USERNAME) {
			throw ApiException.invalid("search", "must be at most " + Limits.USERNAME + " characters");
		}

		Set<String> filter = new TreeSet<>();
		boolean withoutRole = false;
		for (String value : roleCodes) {
			String code = value.strip().toUpperCase(Locale.ROOT);
			if (code.equals(NO_ROLE)) {
				withoutRole = true;
			}
			else {
				filter.add(Role.ofCode(code).orElseThrow(() -> ApiException.invalid("roles", "must hold role codes or " + NO_ROLE)).code());
			}
		}

		Set<String> statuses = new TreeSet<>();
		for (String value : statusCodes) {
			String status = value.strip().toUpperCase(Locale.ROOT);
			if (!STATUSES.contains(status)) {
				throw ApiException.invalid("status", "must hold ACTIVE, DISABLED, or DELETED");
			}
			statuses.add(status);
		}

		ListFilter where = new ListFilter(hiddenRoles, hiddenUserId, part, List.copyOf(filter), withoutRole, List.copyOf(statuses));
		return PageResponse.of(repository.list(where, page), page, user -> new Cursor(user.username(), user.id()));
	}

	/**
	 * A page of the user picker (see The user picker in docs/database/database-design.md): ids and usernames
	 * only, never the SYSTEM identity.
	 *
	 * @param use {@code filter}: every user, for the search filters of the tickets and the audit page, open
	 *     to {@code TICKET_SEARCH} or {@code AUDIT_VIEW}; {@code assignee}: the eligible assignees the caller
	 *     may give a ticket to, open to {@code TICKET_ASSIGN}
	 */
	public PageResponse<UserOption> options(CurrentUser caller, String use, String search, PageQuery page) {
		if (caller.roles().isEmpty()) {
			throw new ApiException(ErrorCode.ACCOUNT_NO_ROLE, "This account is waiting for an administrator to assign permissions");
		}
		String part = search == null || search.isBlank() ? null : search.strip();
		if (part != null && part.length() > Limits.USERNAME) {
			throw ApiException.invalid("search", "must be at most " + Limits.USERNAME + " characters");
		}
		String kind = use == null ? "" : use.strip().toLowerCase(Locale.ROOT);
		List<String> eligible;
		List<String> excluded;
		switch (kind) {
			case "filter" -> {
				if (!caller.can(Permission.TICKET_SEARCH) && !caller.can(Permission.AUDIT_VIEW)) {
					throw new ApiException(ErrorCode.FORBIDDEN, "Your roles do not allow choosing users for a filter");
				}
				eligible = null;
				excluded = List.of();
			}
			case "assignee" -> {
				if (!caller.can(Permission.TICKET_ASSIGN)) {
					throw new ApiException(ErrorCode.FORBIDDEN, "Your roles do not allow assigning tickets");
				}
				eligible = rolesGranting(Permission.TICKET_TAKE);
				excluded = caller.can(Permission.TICKET_ASSIGN_PROTECTED) ? List.of() : rolesGranting(Permission.TICKET_ASSIGN_PROTECTED);
			}
			default -> throw ApiException.invalid("use", "must be filter or assignee");
		}
		return PageResponse.of(repository.options(eligible, excluded, part, page), page, user -> new Cursor(user.username(), user.id()));
	}

	private static List<String> rolesGranting(Permission permission) {
		return Arrays.stream(Role.values()).filter(role -> role.grants(permission)).map(Role::code).toList();
	}

	/**
	 * How many tickets in progress a change of the roles to this list would return to the pool: all of them
	 * when the user can take tickets now and could not with the list, otherwise none. It reads without a
	 * lock, so the Change roles dialog can say it before the change; the change decides again under the lock
	 * of the user row, and answers the number it returned.
	 */
	public int ticketsReturnedBy(CurrentUser actor, long targetId, List<String> roles) {
		var target = repository.findById(targetId).filter(row -> !row.deleted() && !row.system())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "There is no such user"));
		Set<String> wanted = new TreeSet<>();
		for (String code : roles) {
			wanted.add(Role.ofCode(code.strip().toUpperCase(Locale.ROOT))
					.orElseThrow(() -> ApiException.invalid("roles", "must hold only role codes of the system")).code());
		}
		Set<String> current = new TreeSet<>(repository.roleCodes(target.id()));
		if (!UserChangeWriter.takesTickets(current) || UserChangeWriter.takesTickets(wanted)) {
			return 0;
		}
		return repository.countTicketsInProgress(target.id());
	}

	/**
	 * Sets the roles of a user to the given list.
	 *
	 * @param roles the role codes the user is to hold afterwards
	 * @param expectedRoles the role codes the actor saw on the screen
	 */
	public UserRoles replaceRoles(CurrentUser actor, long targetId, List<String> roles, List<String> expectedRoles,
			String ipAddress) {
		Set<Role> wanted = EnumSet.noneOf(Role.class);
		for (String code : roles) {
			wanted.add(Role.ofCode(code).orElseThrow(() -> ApiException.invalid("roles", "must hold only role codes of the system")));
		}
		Set<String> seen = Set.copyOf(expectedRoles);
		return retry.run(() -> writer.replaceRoles(actor.id(), targetId, wanted, seen, ipAddress));
	}

	public UserStatus disable(CurrentUser actor, long targetId, String ipAddress) {
		return retry.run(() -> writer.disable(actor.id(), targetId, ipAddress));
	}

	public UserStatus enable(CurrentUser actor, long targetId, String ipAddress) {
		return retry.run(() -> writer.enable(actor.id(), targetId, ipAddress));
	}

	public void softDelete(CurrentUser actor, long targetId, String ipAddress) {
		retry.run(() -> writer.softDelete(actor.id(), targetId, ipAddress));
	}

}
