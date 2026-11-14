package com.opsflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.opsflow.common.security.RequiresPermission;
import com.opsflow.common.security.AnyAccount;
import com.opsflow.common.security.Public;
import com.opsflow.support.MySqlIntegrationTest;

/**
 * A rule checked by a test (see Rules Checked By Tests in docs/backend/backend-design.md): every endpoint
 * of the application declares who may call it, with exactly one of {@link Public}, {@link AnyAccount},
 * and {@link RequiresPermission}.
 *
 * An endpoint that declares nothing is already closed for everyone ({@link EndpointPermissionTest}), so
 * forgetting the declaration is not a hole. This test makes it a failed build and not a puzzling 403.
 * It reads the endpoints from the running application, so a new controller is covered without a change
 * here. The controllers that exist only in the tests are left out.
 */
class EveryEndpointDeclaresWhoMayCallItTest extends MySqlIntegrationTest {

	private static final List<Class<? extends Annotation>> DECLARATIONS = List.of(Public.class, AnyAccount.class, RequiresPermission.class);

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping endpoints;

	@Test
	void everyEndpointOfTheApplicationDeclaresExactlyOnce() {
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<RequestMappingInfo, HandlerMethod> endpoint : endpoints.getHandlerMethods().entrySet()) {
			HandlerMethod method = endpoint.getValue();
			if (!isApplicationCode(method.getBeanType())) {
				continue;
			}
			checked++;
			long declared = DECLARATIONS.stream().filter(method::hasMethodAnnotation).count();
			if (declared != 1) {
				wrong.add(endpoint.getKey() + " -> " + method + " declares " + declared + " times");
			}
		}

		assertThat(wrong).as("endpoints that must declare @Public, @AnyAccount, or @RequiresPermission exactly once").isEmpty();
		// The five endpoints of login, sign-up, and recovery at least: an empty list would pass for the wrong reason.
		assertThat(checked).isGreaterThanOrEqualTo(5);
	}

	@Test
	void theControllersOfTheTestsAreLeftOutAndThoseOfTheApplicationAreNot() {
		assertThat(isApplicationCode(PermissionProbeController.class)).isFalse();
		assertThat(isApplicationCode(AuthController.class)).isTrue();
	}

	/** A class of this project that is compiled from src/main, and not from src/test or a library. */
	private static boolean isApplicationCode(Class<?> type) {
		if (!type.getName().startsWith("com.opsflow.")) {
			return false;
		}
		String location = type.getProtectionDomain().getCodeSource().getLocation().getPath();
		return !location.contains("test-classes");
	}

}
