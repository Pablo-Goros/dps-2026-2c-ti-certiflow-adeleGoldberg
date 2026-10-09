package ar.edu.itba.dps.certification.app.web.dto;

import java.time.Instant;

/** Body of every error response. {@code code} is stable and meant for programs; {@code message} for people. */
public record ApiError(int status, String code, String message, Instant timestamp) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, Instant.now());
    }
}
