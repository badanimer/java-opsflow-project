package com.opsflow.common.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.opsflow.common.config.OpsflowProperties;
import com.opsflow.common.error.ApiExceptionHandler;
import com.opsflow.common.paging.PageQueryFactory;

/**
 * The shared conventions of docs/api/api-design.md, checked through HTTP calls to {@link ProbeController}:
 * the base path, the error body, the result codes, the paging parameters, the idempotency header, and the
 * format of times. The service layer is not involved.
 */
@WebMvcTest(ProbeController.class)
@Import({ WebConfig.class, ApiExceptionHandler.class, PageQueryFactory.class })
@EnableConfigurationProperties(OpsflowProperties.class)
class ApiConventionsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void everyControllerIsServedUnderTheBasePath() throws Exception {
		mvc.perform(get("/api/v1/probe/ok")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
		mvc.perform(get("/probe/ok")).andExpect(status().isNotFound());
	}

	@Test
	void anApiRefusalHasTheStatusTheCodeAndItsExtraFields() throws Exception {
		mvc.perform(get("/api/v1/probe/refusal"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
				.andExpect(jsonPath("$.title").value("Out of stock"))
				.andExpect(jsonPath("$.detail").value("Widget has only 3 left"))
				.andExpect(jsonPath("$.orderNo").value("ord-3f2a"));
	}

	@Test
	void busyIs503() throws Exception {
		mvc.perform(get("/api/v1/probe/busy"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("BUSY"));
	}

	@Test
	void aValidationFailureListsEveryRefusedField() throws Exception {
		mvc.perform(post("/api/v1/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"TOO-LONG-SKU\",\"quantity\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field", hasItem("sku")))
				.andExpect(jsonPath("$.errors[*].field", hasItem("quantity")));
	}

	@Test
	void aTextLongerThanItsColumnIsRefusedAndNeverTruncated() throws Exception {
		mvc.perform(post("/api/v1/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"123456789\",\"quantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("sku"));
		mvc.perform(post("/api/v1/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"12345678\",\"quantity\":1}"))
				.andExpect(status().isOk());
	}

	@Test
	void aMalformedBodyIsAValidationFailure() throws Exception {
		mvc.perform(post("/api/v1/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void aRequestParameterThatBreaksItsConstraintIsAValidationFailure() throws Exception {
		mvc.perform(get("/api/v1/probe/param").param("n", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("n"));
	}

	@Test
	void aParameterOfTheWrongTypeOrMissingIsAValidationFailure() throws Exception {
		mvc.perform(get("/api/v1/probe/param").param("n", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mvc.perform(get("/api/v1/probe/param"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void anUnknownPathIsNotFound() throws Exception {
		mvc.perform(get("/api/v1/nothing-here"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void aWrongMethodAndAWrongMediaTypeHaveTheirOwnCodes() throws Exception {
		mvc.perform(put("/api/v1/probe/ok"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
		mvc.perform(post("/api/v1/probe/body").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	void anUnexpectedErrorShowsNoDetailAndNoSqlOrSecret() throws Exception {
		mvc.perform(get("/api/v1/probe/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").doesNotExist())
				.andExpect(content().string(not(containsString("insert"))))
				.andExpect(content().string(not(containsString("secret"))))
				.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@Test
	void anInternalErrorThrownOnPurposeIsNotExplainedEither() throws Exception {
		mvc.perform(get("/api/v1/probe/internal"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("stack trace"))));
	}

	@Test
	void thePagingParametersAreCheckedAndDefaulted() throws Exception {
		mvc.perform(get("/api/v1/probe/page"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.limit").value(10))
				.andExpect(jsonPath("$.direction").value("NEXT"))
				.andExpect(jsonPath("$.order").value("DESC"));
		mvc.perform(get("/api/v1/probe/page").param("limit", "11"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("limit"));
		mvc.perform(get("/api/v1/probe/page").param("cursor", "not-a-cursor"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("cursor"));
	}

	@Test
	void theIdempotencyKeyIsOptionalAndChecked() throws Exception {
		mvc.perform(get("/api/v1/probe/key")).andExpect(status().isOk()).andExpect(jsonPath("$.key").value("none"));
		mvc.perform(get("/api/v1/probe/key").header(IdempotencyKey.HEADER, "7c9e6679-7425-40de-944b-e07fc1f90ae7"))
				.andExpect(jsonPath("$.key").value("7c9e6679-7425-40de-944b-e07fc1f90ae7"));
		mvc.perform(get("/api/v1/probe/key").header(IdempotencyKey.HEADER, "two words"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));
	}

	@Test
	void aTimeIsWrittenInIso8601WithATrailingZ() throws Exception {
		mvc.perform(get("/api/v1/probe/time"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.at").value("2026-11-04T03:00:00Z"));
	}

}
