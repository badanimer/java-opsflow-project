package com.opsflow.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * The one place where an exception becomes an error response. Every error is a Problem Details body
 * (RFC 9457, {@code application/problem+json}) with an added {@code code}; see docs/api/api-design.md.
 *
 * The body never carries a stack trace or an SQL message. An unexpected error is logged here with its
 * stack trace and answered with {@code INTERNAL_ERROR} and no detail.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApi(ApiException e) {
		ErrorCode code = e.code();
		if (code == ErrorCode.INTERNAL_ERROR || code == ErrorCode.BUSY) {
			log.warn("Answering {}", code, e);
		}
		ProblemDetail body = problem(code, code == ErrorCode.INTERNAL_ERROR ? null : e.getMessage());
		e.extras().forEach(body::setProperty);
		return respond(body);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
			String path = violation.getPropertyPath().toString();
			errors.add(Map.of("field", path.substring(path.lastIndexOf('.') + 1), "message", String.valueOf(violation.getMessage())));
		}
		return respond(validation(errors));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return respond(validation(List.of(Map.of("field", String.valueOf(e.getName()), "message", "has the wrong type"))));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
		log.error("Unexpected error", e);
		return respond(problem(ErrorCode.INTERNAL_ERROR, null));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			errors.add(Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())));
		}
		for (ObjectError error : e.getBindingResult().getGlobalErrors()) {
			errors.add(Map.of("field", error.getObjectName(), "message", String.valueOf(error.getDefaultMessage())));
		}
		return respondObject(validation(errors), headers);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = new ArrayList<>();
		for (ParameterValidationResult result : e.getParameterValidationResults()) {
			String parameter = String.valueOf(result.getMethodParameter().getParameterName());
			if (result instanceof ParameterErrors parameterErrors) {
				for (FieldError error : parameterErrors.getFieldErrors()) {
					errors.add(Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())));
				}
			}
			else {
				for (MessageSourceResolvable error : result.getResolvableErrors()) {
					errors.add(Map.of("field", parameter, "message", String.valueOf(error.getDefaultMessage())));
				}
			}
		}
		return respondObject(validation(errors), headers);
	}

	/** Every other error that Spring MVC raises itself: unreadable body, missing parameter, unknown path, wrong method. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ErrorCode code = codeFor(status);
		if (status.is5xxServerError()) {
			log.error("Spring MVC failed with status {}", status.value(), e);
		}
		return respondObject(problem(code, code == ErrorCode.INTERNAL_ERROR ? null : detailOf(body)), headers);
	}

	private static ErrorCode codeFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> ErrorCode.NOT_FOUND;
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
			default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
		};
	}

	private static String detailOf(Object body) {
		return body instanceof ProblemDetail problem ? problem.getDetail() : null;
	}

	private static ProblemDetail validation(List<Map<String, String>> errors) {
		ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "Request validation failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	private static ProblemDetail problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		problem.setTitle(code.title());
		if (detail != null) {
			problem.setDetail(detail);
		}
		problem.setProperty("code", code.name());
		return problem;
	}

	private static ResponseEntity<ProblemDetail> respond(ProblemDetail body) {
		return ResponseEntity.status(body.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}

	private static ResponseEntity<Object> respondObject(ProblemDetail body, HttpHeaders headers) {
		HttpHeaders copy = new HttpHeaders();
		copy.putAll(headers);
		copy.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		return new ResponseEntity<>(body, copy, HttpStatus.valueOf(body.getStatus()));
	}

}
