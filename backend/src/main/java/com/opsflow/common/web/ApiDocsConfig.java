package com.opsflow.common.web;

import java.util.List;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.opsflow.common.security.AnyAccount;
import com.opsflow.common.security.Public;
import com.opsflow.common.security.RequiresPermission;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * The API documentation (see API documentation in docs/backend/backend-design.md): the OpenAPI description
 * at {@value #DOCS_JSON} and Swagger UI at {@value #DOCS_PAGE}, both under the base path, so the reverse
 * proxy and the development server pass them on as they pass the API.
 *
 * <p>Each operation says who may call it, read from the declaration its controller method already carries
 * ({@code @Public}, {@code @AnyAccount}, or {@code @RequiresPermission}), so the page cannot disagree with
 * the check. Reading the documentation needs no login; calling an endpoint from it needs a token, given
 * once with Authorize.
 */
@Configuration
public class ApiDocsConfig {

	public static final String DOCS_JSON = WebConfig.BASE_PATH + "/api-docs";
	public static final String DOCS_PAGE = WebConfig.BASE_PATH + "/docs";
	/** The paths Swagger UI is served from: its page, its files, and the description it reads. */
	public static final List<String> PUBLIC_PATHS = List.of(DOCS_PAGE, DOCS_JSON, DOCS_JSON + "/**", WebConfig.BASE_PATH + "/swagger-ui/**");

	private static final String BEARER = "bearer";

	@Bean
	OpenAPI openApi() {
		// The version of the jar, which Maven writes into its manifest; none when the classes run unpacked.
		String version = ApiDocsConfig.class.getPackage().getImplementationVersion();
		return new OpenAPI()
				.info(new Info().title("OpsFlow API")
						.version(version == null ? "development" : version)
						.description("Orders, inventory, and operations tickets. Log in with POST /auth/login, then use Authorize with the token. "
								+ "Errors are Problem Details with a result code; lists are paged by key. "
								+ "The conventions are in docs/api/api-design.md.")
						.license(new License().name("MIT")))
				.components(new Components().addSecuritySchemes(BEARER,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

	/** Writes who may call an operation, and asks for the token on every one that is not public. */
	@Bean
	OperationCustomizer whoMayCall() {
		return (operation, method) -> {
			String who;
			if (method.hasMethodAnnotation(Public.class)) {
				who = "Public: no login needed.";
			}
			else if (method.hasMethodAnnotation(AnyAccount.class)) {
				who = "Any logged-in account.";
			}
			else {
				RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
				who = required == null ? "Nobody: the endpoint declares no permission." : "Needs the permission " + required.value().name() + ".";
			}
			if (!method.hasMethodAnnotation(Public.class)) {
				operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
			}
			operation.setDescription(operation.getDescription() == null ? who : who + "\n\n" + operation.getDescription());
			return operation;
		};
	}

}
