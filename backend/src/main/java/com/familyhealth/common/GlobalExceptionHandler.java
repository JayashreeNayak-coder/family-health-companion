package com.familyhealth.common;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into RFC 9457 Problem Details with a stable code (docs/05 section 2).
 * Security filter failures reach this class too, so there is one error format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final URI ABOUT_BLANK = URI.create("about:blank");

	@ExceptionHandler(RateLimitedException.class)
	ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
		return ResponseEntity.status(ex.status())
			.header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
			.body(problem(ex.status(), ex.code(), ex.getMessage()));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		return ResponseEntity.status(ex.status()).body(problem(ex.status(), ex.code(), ex.getMessage()));
	}

	/** Two writes raced, and the database or the version column stopped the second. */
	@ExceptionHandler({ ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class })
	ResponseEntity<ProblemDetail> handleConflict(Exception ex) {
		// The class name only: these messages can quote the submitted values.
		log.warn("Write conflict: {}", ex.getClass().getSimpleName());
		HttpStatus status = HttpStatus.CONFLICT;
		return ResponseEntity.status(status)
			.body(problem(status, "CONFLICT", "This could not be saved because of a conflicting change"));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException ex) {
		HttpStatus status = HttpStatus.UNAUTHORIZED;
		return ResponseEntity.status(status).body(problem(status, "UNAUTHENTICATED", "Sign in to continue"));
	}

	@ExceptionHandler(CsrfException.class)
	ResponseEntity<ProblemDetail> handleCsrf(CsrfException ex) {
		HttpStatus status = HttpStatus.FORBIDDEN;
		return ResponseEntity.status(status).body(problem(status, "CSRF_REJECTED", "Request could not be verified"));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> handleForbidden(AccessDeniedException ex) {
		HttpStatus status = HttpStatus.FORBIDDEN;
		return ResponseEntity.status(status).body(problem(status, "FORBIDDEN", "Access denied"));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(status).body(problem(status, "INTERNAL_ERROR", "Something went wrong"));
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList();
		ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation failed");
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/** Framework errors (malformed JSON, wrong method, unknown path) get a code too, and lose their detail text. */
	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			problem.setDetail(null);
			problem.setType(ABOUT_BLANK);
			if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
				HttpStatus status = HttpStatus.resolve(statusCode.value());
				problem.setProperty("code", status != null ? status.name() : "ERROR");
			}
		}
		return response;
	}

	private static ProblemDetail problem(HttpStatus status, String code, String title) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setType(ABOUT_BLANK);
		problem.setTitle(title);
		problem.setProperty("code", code);
		return problem;
	}

}
