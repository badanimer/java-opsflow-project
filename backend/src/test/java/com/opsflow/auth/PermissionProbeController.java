package com.opsflow.auth;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.security.AnyAccount;
import com.opsflow.common.security.Permission;
import com.opsflow.common.security.Public;
import com.opsflow.common.security.RequiresPermission;

/**
 * A controller that exists only in the tests, with one endpoint for each way an endpoint can declare who
 * may call it, and one that declares nothing. {@link EndpointPermissionTest} calls them.
 */
@RestController
@RequestMapping("/permission-probe")
public class PermissionProbeController {

	private static final Map<String, Object> OK = Map.of("ok", true);

	@Public
	@GetMapping("/public")
	public Map<String, Object> everyone() {
		return OK;
	}

	@AnyAccount
	@GetMapping("/any-account")
	public Map<String, Object> anyAccount() {
		return OK;
	}

	/** Granted to administrators and managers. */
	@RequiresPermission(Permission.AUDIT_VIEW)
	@GetMapping("/managers")
	public Map<String, Object> managers() {
		return OK;
	}

	/** Granted to operators, managers, and administrators, and not to viewers. */
	@RequiresPermission(Permission.ORDER_PLACE)
	@GetMapping("/operators")
	public Map<String, Object> operators() {
		return OK;
	}

	/** Granted to viewers, managers, and administrators, and not to operators. */
	@RequiresPermission(Permission.TICKET_SEARCH)
	@GetMapping("/viewers")
	public Map<String, Object> viewers() {
		return OK;
	}

	/** Declares nothing, like an endpoint whose author forgot. */
	@GetMapping("/undeclared")
	public Map<String, Object> undeclared() {
		return OK;
	}

}
