package com.opsflow.user;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.opsflow.common.error.ApiException;
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
	 * @param page the paging, in the one order of this list, from A to Z
	 */
	public PageResponse<ListedUser> list(CurrentUser viewer, String search, List<String> roleCodes, PageQuery page) {
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

		ListFilter where = new ListFilter(hiddenRoles, hiddenUserId, part, List.copyOf(filter), withoutRole);
		return PageResponse.of(repository.list(where, page), page, user -> new Cursor(user.username(), user.id()));
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
