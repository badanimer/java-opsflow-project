package com.opsflow.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.opsflow.common.audit.AuditAction;
import com.opsflow.common.audit.AuditLogWriter;
import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.Role;
import com.opsflow.user.UserRepository.UserRow;

/**
 * The transactions in which one user changes another. They follow Steps of a change to a user in
 * docs/database/database-design.md:
 *
 * <ol>
 * <li>Refuse at once when the target is the actor.</li>
 * <li>Lock the user rows of the actor and of the target, in ascending id.</li>
 * <li>Read the status and the roles of both under those locks, and check the authority of the actor as
 * it is now: the roles the request arrived with may have been taken away since.</li>
 * <li>Compare with what the actor saw on the screen.</li>
 * <li>If the target holds {@code ADMIN}, lock the {@code ADMIN} row in {@code roles}.</li>
 * <li>Apply the change.</li>
 * <li>If step 5 applied, count the usable administrators, and roll back when there is none.</li>
 * <li>Write the log rows.</li>
 * </ol>
 *
 * Lock order: {@code users} (ascending id), then {@code roles}. Every refusal is thrown, so the
 * transaction rolls back and nothing is written. The caller runs each method through
 * {@code TransactionRetry}.
 */
@Component
public class UserChangeWriter {

	/** The {@code remark} of the log rows of one replacement of roles. */
	static final String REPLACE_ROLES = "replace roles";

	private static final String ACTIVE = "ACTIVE";
	private static final String DISABLED = "DISABLED";

	/** The role that must always have a usable holder (see Administrator protection). */
	private static final Role GUARDED = Role.ADMIN;

	/** The roles of a user after a change. */
	public record UserRoles(long id, String username, List<String> roles) {
	}

	/** The status of a user after a change: {@code ACTIVE} or {@code DISABLED}. */
	public record UserStatus(long id, String username, String status) {
	}

	/** A locked user with the roles read under the lock. */
	private record Party(UserRow user, Set<String> roles) {

		boolean can(Permission permission) {
			return roles.stream().anyMatch(code -> Role.ofCode(code).filter(role -> role.grants(permission)).isPresent());
		}

		boolean holdsProtectedRole() {
			return roles.stream().anyMatch(UserChangeWriter::isProtected);
		}
	}

	private record Parties(Party actor, Party target) {
	}

	private final UserRepository repository;
	private final AuditLogWriter audit;

	UserChangeWriter(UserRepository repository, AuditLogWriter audit) {
		this.repository = repository;
		this.audit = audit;
	}

	/**
	 * Sets the roles of a user to the given list (see Role management).
	 *
	 * @param wanted the roles the user is to hold afterwards; empty leaves the user with no role
	 * @param seen the role codes the actor saw on the screen
	 * @return the roles the user holds after the change
	 */
	@Transactional
	public UserRoles replaceRoles(long actorId, long targetId, Set<Role> wanted, Set<String> seen, String ipAddress) {
		Parties parties = lock(actorId, targetId);
		Party actor = parties.actor();
		Party target = parties.target();

		// Step 3: the authority over the target as it is now, and over the change that is asked for.
		Set<String> current = target.roles();
		Set<String> wantedCodes = new TreeSet<>();
		wanted.forEach(role -> wantedCodes.add(role.code()));
		Set<String> removed = new TreeSet<>(current);
		removed.removeAll(wantedCodes);
		Set<String> added = new TreeSet<>(wantedCodes);
		added.removeAll(current);

		require(actor, Permission.ROLE_ASSIGN);
		boolean touchesProtected = target.holdsProtectedRole() || added.stream().anyMatch(UserChangeWriter::isProtected);
		if (touchesProtected) {
			require(actor, Permission.ROLE_ASSIGN_PROTECTED);
		}

		// Step 4: what the actor saw is still what the user holds.
		if (!current.equals(seen)) {
			throw new ApiException(ErrorCode.USER_CHANGED, "The roles of this user were changed by someone else, please reload");
		}
		if (added.isEmpty() && removed.isEmpty()) {
			return new UserRoles(targetId, target.user().username(), List.copyOf(current));
		}
		if (!added.isEmpty() && !target.user().active()) {
			throw new ApiException(ErrorCode.USER_NOT_ACTIVE, "A disabled user cannot be given a role. Enable the user first");
		}

		// Step 5.
		boolean guarded = current.contains(GUARDED.code());
		if (guarded) {
			repository.lockRole(GUARDED.code());
		}

		// Step 6.
		Map<String, Long> roleIds = repository.roleIds();
		for (String code : removed) {
			repository.revoke(targetId, roleIds.get(code));
		}
		for (String code : added) {
			repository.grant(targetId, roleIds.get(code));
		}

		// Step 7.
		if (guarded) {
			requireAnAdministrator();
		}

		// Step 8.
		String username = target.user().username();
		for (String code : removed) {
			audit.write(AuditAction.ROLE_REVOKED, actorId, AuditLogWriter.TARGET_USER, targetId,
					roleData(roleIds.get(code), code, username), null, ipAddress, REPLACE_ROLES);
		}
		for (String code : added) {
			audit.write(AuditAction.ROLE_GRANTED, actorId, AuditLogWriter.TARGET_USER, targetId, null,
					roleData(roleIds.get(code), code, username), ipAddress, REPLACE_ROLES);
		}
		return new UserRoles(targetId, username, List.copyOf(wantedCodes));
	}

	/**
	 * Disables an account. The roles stay, and count for nothing until the account is enabled again
	 * (see Role grants). The next request of the user is answered {@code ACCOUNT_DISABLED}.
	 *
	 * The tickets the user holds are not returned to the pool yet: that step belongs to the ticket rules
	 * and is added with the ticket module (T-35), as for a soft deletion.
	 */
	@Transactional
	public UserStatus disable(long actorId, long targetId, String ipAddress) {
		Party target = lockForStatusChange(actorId, targetId);
		if (!target.user().active()) {
			throw statusChanged("This user is already disabled", DISABLED);
		}
		boolean guarded = lockGuardedRole(target);
		repository.setStatus(targetId, DISABLED);
		if (guarded) {
			requireAnAdministrator();
		}
		audit.write(AuditAction.USER_STATUS_CHANGED, actorId, AuditLogWriter.TARGET_USER, targetId, Map.of("status", ACTIVE),
				statusData(DISABLED, target), ipAddress, null);
		return new UserStatus(targetId, target.user().username(), DISABLED);
	}

	/** Enables a disabled account. The roles it still holds take effect again. */
	@Transactional
	public UserStatus enable(long actorId, long targetId, String ipAddress) {
		Party target = lockForStatusChange(actorId, targetId);
		if (target.user().active()) {
			throw statusChanged("This user is already active", ACTIVE);
		}
		repository.setStatus(targetId, ACTIVE);
		audit.write(AuditAction.USER_STATUS_CHANGED, actorId, AuditLogWriter.TARGET_USER, targetId, Map.of("status", DISABLED),
				statusData(ACTIVE, target), ipAddress, null);
		return new UserStatus(targetId, target.user().username(), ACTIVE);
	}

	/**
	 * Soft-deletes an account, active or disabled: it is marked as removed and disabled. The row, the
	 * roles, the username, and the email stay, so the history still reads and the values are never reused.
	 */
	@Transactional
	public void softDelete(long actorId, long targetId, String ipAddress) {
		Party target = lockForStatusChange(actorId, targetId);
		// A disabled administrator is not a usable one, so removing it cannot take the last one away.
		boolean guarded = target.user().active() && lockGuardedRole(target);
		repository.softDelete(targetId);
		if (guarded) {
			requireAnAdministrator();
		}
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("status", target.user().active() ? ACTIVE : DISABLED);
		before.put("deleted", false);
		Map<String, Object> after = statusData(DISABLED, target);
		after.put("deleted", true);
		audit.write(AuditAction.USER_DELETED, actorId, AuditLogWriter.TARGET_USER, targetId, before, after, ipAddress, null);
	}

	/** Steps 1 to 3 of a change to the status of an account, with the authority it needs. */
	private Party lockForStatusChange(long actorId, long targetId) {
		Parties parties = lock(actorId, targetId);
		require(parties.actor(), Permission.USER_MANAGE);
		if (parties.target().holdsProtectedRole()) {
			require(parties.actor(), Permission.USER_MANAGE_PROTECTED);
		}
		return parties.target();
	}

	/** Step 5: locks the row of the guarded role when the target holds it. */
	private boolean lockGuardedRole(Party target) {
		boolean guarded = target.roles().contains(GUARDED.code());
		if (guarded) {
			repository.lockRole(GUARDED.code());
		}
		return guarded;
	}

	/** Step 4 of a status change: the status is not the one the change starts from. The answer says which it is. */
	private static ApiException statusChanged(String detail, String currentStatus) {
		return new ApiException(ErrorCode.USER_CHANGED, detail).with("userStatus", currentStatus);
	}

	/** The data of an account row: the status, and the username as the label of the row. */
	private static Map<String, Object> statusData(String status, Party user) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("status", status);
		data.put("username", user.user().username());
		return data;
	}

	/**
	 * Steps 1 to 3, as far as every change shares them: the two locks, and the checks that the actor is
	 * still a usable account and that the target is a person who was not deleted.
	 */
	private Parties lock(long actorId, long targetId) {
		if (actorId == targetId) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Nobody changes their own account. Another administrator must do it");
		}
		UserRow first = repository.lockById(Math.min(actorId, targetId)).orElse(null);
		UserRow second = repository.lockById(Math.max(actorId, targetId)).orElse(null);
		UserRow actor = actorId < targetId ? first : second;
		UserRow target = actorId < targetId ? second : first;

		// The same answers as the token check gives on the next request of this account.
		if (actor == null || actor.deleted() || actor.system()) {
			throw new ApiException(ErrorCode.UNAUTHENTICATED);
		}
		if (!actor.active()) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is disabled");
		}
		// The SYSTEM identity is not a person and is in no list, so for a caller it does not exist.
		if (target == null || target.deleted() || target.system()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "There is no such user");
		}
		return new Parties(new Party(actor, rolesOf(actorId)), new Party(target, rolesOf(targetId)));
	}

	private Set<String> rolesOf(long userId) {
		return new TreeSet<>(repository.roleCodes(userId));
	}

	/** The actor may do this with the roles they hold now, under the lock. */
	private static void require(Party actor, Permission permission) {
		if (actor.can(permission)) {
			return;
		}
		if (actor.roles().isEmpty()) {
			throw new ApiException(ErrorCode.ACCOUNT_NO_ROLE, "This account is waiting for an administrator to assign permissions");
		}
		throw new ApiException(ErrorCode.FORBIDDEN, "Your roles do not allow this change to this user");
	}

	/** Step 7. Called with the row of the guarded role locked, after the change was applied. */
	private void requireAnAdministrator() {
		if (repository.countUsableHolders(GUARDED.code()) == 0) {
			throw new ApiException(ErrorCode.LAST_ADMIN, "The system must keep at least one usable administrator");
		}
	}

	private static boolean isProtected(String roleCode) {
		return Role.ofCode(roleCode).filter(Role::isProtected).isPresent();
	}

	/** The data of a role row: the role, and the username as the label of the row. */
	private static Map<String, Object> roleData(long roleId, String roleCode, String username) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("roleId", roleId);
		data.put("roleCode", roleCode);
		data.put("username", username);
		return data;
	}

}
