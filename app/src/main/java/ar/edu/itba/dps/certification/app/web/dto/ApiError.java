package ar.edu.itba.dps.certification.app.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Body of every error response. {@code code} is stable and meant for programs; {@code message}
 * for people; {@code details} lists individual reasons when there are several.
 */
public record ApiError(int status, String code, String message, List<String> details, Instant timestamp) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, List.of(), Instant.now());
    }

    public static ApiError of(int status, String code, String message, List<String> details) {
        return new ApiError(status, code, message, List.copyOf(details), Instant.now());
    }
}
