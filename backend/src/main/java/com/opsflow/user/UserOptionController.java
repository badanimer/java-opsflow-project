package com.opsflow.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageQueryFactory;
import com.opsflow.common.paging.PageResponse;
import com.opsflow.common.paging.SortOrder;
import com.opsflow.common.security.AnyAccount;
import com.opsflow.common.security.CurrentUser;
import com.opsflow.user.UserRepository.UserOption;

/**
 * The user picker, {@code GET /user-options} (see Tickets in docs/api/api-design.md). It is open to any
 * account at the gate because each use needs a different permission, one of two for the filters; the
 * service checks the permission of the use and answers {@code ACCOUNT_NO_ROLE} or {@code FORBIDDEN}.
 */
@RestController
public class UserOptionController {

	public record UserOptionResponse(long id, String username) {
	}

	private final UserService users;
	private final PageQueryFactory pages;

	UserOptionController(UserService users, PageQueryFactory pages) {
		this.users = users;
		this.pages = pages;
	}

	@AnyAccount
	@GetMapping("/user-options")
	public PageResponse<UserOptionResponse> options(@RequestParam(required = false) String use, @RequestParam(required = false) String search,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order,
			@AuthenticationPrincipal CurrentUser user) {
		PageQuery page = pages.createInOrder(limit, cursor, direction, order, SortOrder.ASC);
		PageResponse<UserOption> found = users.options(user, use, search, page);
		return new PageResponse<>(found.items().stream().map(row -> new UserOptionResponse(row.id(), row.username())).toList(),
				found.nextCursor(), found.prevCursor());
	}

}
