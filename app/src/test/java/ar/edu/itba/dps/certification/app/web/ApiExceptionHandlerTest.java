package ar.edu.itba.dps.certification.app.web;

import ar.edu.itba.dps.certification.app.config.AuthenticationRequiredException;
import ar.edu.itba.dps.certification.app.web.dto.ApiError;
import ar.edu.itba.dps.certification.application.shared.ConflictException;
import ar.edu.itba.dps.certification.domain.shared.DomainException;
import ar.edu.itba.dps.certification.domain.shared.InvalidArgumentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The error contract of the API: one status and one stable code per kind of failure. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private static void assertReply(ResponseEntity<ApiError> reply, int status, String code) {
        assertThat(reply.getStatusCode().value()).isEqualTo(status);
        assertThat(reply.getBody()).isNotNull();
        assertThat(reply.getBody().status()).isEqualTo(status);
        assertThat(reply.getBody().code()).isEqualTo(code);
    }

    @Test
    void malformedInputIs400() {
        var reply = handler.invalidArgument(new InvalidArgumentException("name is required"));

        assertReply(reply, 400, "INVALID_INPUT");
        assertThat(reply.getBody().message()).isEqualTo("name is required");
    }

    @Test
    void aBusinessRuleViolationIs422() {
        var reply = handler.businessRule(new DomainException("the asset already holds a certificate"));

        assertReply(reply, 422, "BUSINESS_RULE");
        assertThat(reply.getBody().message()).contains("already holds");
    }

    @Test
    void aRefusedPublicationIs422AndCarriesEveryViolation() {
        var reply = handler.publicationRefused(new PublicationRefusedException(List.of("first", "second")));

        assertReply(reply, 422, "SCHEMA_NOT_PUBLISHABLE");
        assertThat(reply.getBody().details()).containsExactly("first", "second");
    }

    @Test
    void aMissingResourceIs404() {
        assertReply(handler.notFound(new NotFoundException("asset x does not exist")), 404, "NOT_FOUND");
    }

    @Test
    void aMissingActorIs401() {
        assertReply(handler.unauthenticated(new AuthenticationRequiredException("send X-Actor-Id")), 401,
                "ACTOR_REQUIRED");
    }

    @Test
    void anyConflictIs409WithoutLeakingStorageDetails() {
        var reply = handler.conflict(new ConflictException("duplicate key value violates constraint UQ_SCHEMA"));

        assertReply(reply, 409, "CONFLICT");
        assertThat(reply.getBody().message()).doesNotContain("UQ_SCHEMA");
    }

    @Test
    void anythingElseIs500AndHidesTheCause() {
        var reply = handler.unexpected(new IllegalStateException("secret internal detail"));

        assertReply(reply, 500, "INTERNAL_ERROR");
        assertThat(reply.getBody().message()).doesNotContain("secret");
    }

    @Test
    void anUnreadableRequestIs400() {
        assertReply(handler.malformed(new IllegalArgumentException("bad json")), 400, "MALFORMED_REQUEST");
    }

    @Test
    void anUnknownRouteKeepsItsNotFoundStatusInsteadOfBecoming500() {
        var reply = handler.unexpected(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertReply(reply, 404, "NOT_FOUND");
    }

    @Test
    void otherClientErrorsOfTheFrameworkKeepTheirStatus() {
        var reply = handler.unexpected(new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED));

        assertReply(reply, 405, "REQUEST_REFUSED");
    }

    @Test
    void aServerErrorOfTheFrameworkIsStillAnOpaque500() {
        var reply = handler.unexpected(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream says no"));

        assertReply(reply, 500, "INTERNAL_ERROR");
        assertThat(reply.getBody().message()).doesNotContain("upstream");
    }
}
