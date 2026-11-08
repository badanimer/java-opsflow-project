package com.opsflow.common.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opsflow.common.error.ApiException;
import com.opsflow.common.error.ErrorCode;
import com.opsflow.common.paging.PageQuery;
import com.opsflow.common.paging.PageQueryFactory;
import com.opsflow.common.validation.Limits;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A controller that exists only in the tests, so that the shared web conventions are checked through real HTTP calls. */
@RestController
@RequestMapping("/probe")
public class ProbeController {

	public record Body(@NotBlank @Size(max = Limits.SKU) String sku, @Min(1) Integer quantity) {
	}

	private final PageQueryFactory pageQueryFactory;

	public ProbeController(PageQueryFactory pageQueryFactory) {
		this.pageQueryFactory = pageQueryFactory;
	}

	@GetMapping("/ok")
	public Map<String, Object> ok() {
		return Map.of("ok", true);
	}

	@PostMapping("/body")
	public Map<String, Object> body(@Valid @RequestBody Body body) {
		return Map.of("sku", body.sku());
	}

	@GetMapping("/param")
	public Map<String, Object> param(@RequestParam @Min(1) int n) {
		return Map.of("n", n);
	}

	@GetMapping("/refusal")
	public Map<String, Object> refusal() {
		throw new ApiException(ErrorCode.OUT_OF_STOCK, "Widget has only 3 left").with("orderNo", "ord-3f2a");
	}

	@GetMapping("/busy")
	public Map<String, Object> busy() {
		throw new ApiException(ErrorCode.BUSY, "The system is busy, please try again");
	}

	@GetMapping("/boom")
	public Map<String, Object> boom() {
		throw new IllegalStateException("could not execute statement [insert into users (password_hash) values ('$2a$10$secret')]");
	}

	@GetMapping("/internal")
	public Map<String, Object> internal() {
		throw new ApiException(ErrorCode.INTERNAL_ERROR, "stack trace and SQL that must not leak");
	}

	@GetMapping("/page")
	public PageQuery page(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
			@RequestParam(required = false) String direction, @RequestParam(required = false) String order) {
		return pageQueryFactory.create(limit, cursor, direction, order, false);
	}

	@GetMapping("/key")
	public Map<String, Object> key(@RequestHeader(value = IdempotencyKey.HEADER, required = false) String key) {
		return Map.of("key", IdempotencyKey.parse(key).orElse("none"));
	}

	@GetMapping("/time")
	public Map<String, Object> time() {
		return Map.of("at", Instant.parse("2026-11-04T03:00:00Z"));
	}

}
