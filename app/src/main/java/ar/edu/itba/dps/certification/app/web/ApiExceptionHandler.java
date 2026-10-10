package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.AuthenticationRequiredException;
import ar.edu.itba.dps.certification.app.web.dto.ApiError;
import ar.edu.itba.dps.certification.application.shared.ConflictException;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * One place that turns exceptions into the HTTP contract:
 * 400 malformed input, 401 no/unknown actor, 404 missing resource,
 * 409 concurrent change or duplicate, 422 business rule refused, the framework's own 4xx (unknown
 * route, wrong method...) as they are, 500 anything else.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidArgumentException.class)
    ResponseEntity<ApiError> invalidArgument(InvalidArgumentException e) {
        return reply(HttpStatus.BAD_REQUEST, "INVALID_INPUT", e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> businessRule(DomainException e) {
        return reply(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", e.getMessage());
    }

    @ExceptionHandler(PublicationRefusedException.class)
    ResponseEntity<ApiError> publicationRefused(PublicationRefusedException e) {
        var status = HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(ApiError.of(status.value(),
                "SCHEMA_NOT_PUBLISHABLE", e.getMessage(), e.violations()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return reply(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationRequiredException e) {
        return reply(HttpStatus.UNAUTHORIZED, "ACTOR_REQUIRED", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException e) {
        return reply(HttpStatus.CONFLICT, "CONFLICT",
                "the resource was changed concurrently or already exists; reload and retry");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception e) {
        return reply(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "the request body or a parameter could not be understood");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        if (e instanceof ErrorResponse standard && standard.getStatusCode().is4xxClientError()) {
            return standardClientError(standard);
        }
        log.error("unexpected failure", e);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "unexpected error");
    }

    /** Spring's own client errors (unknown route, wrong method, unsupported media type) keep their status. */
    private static ResponseEntity<ApiError> standardClientError(ErrorResponse error) {
        int status = error.getStatusCode().value();
        if (status == HttpStatus.NOT_FOUND.value()) {
            return reply(HttpStatus.NOT_FOUND, "NOT_FOUND", "there is nothing at this address");
        }
        HttpStatus known = HttpStatus.resolve(status);
        String reason = known == null ? "request refused" : known.getReasonPhrase().toLowerCase();
        return ResponseEntity.status(status).body(ApiError.of(status, "REQUEST_REFUSED", reason));
    }

    private static ResponseEntity<ApiError> reply(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), code, message));
    }
}
