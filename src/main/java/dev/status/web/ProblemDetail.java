package dev.status.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * RFC 9457 problem-details envelope. Optional fields ({@code instance},
 * {@code requestId}, {@code errors}) are omitted from the serialized JSON when
 * absent, keeping the wire payload byte-identical to api-contract §4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String requestId,
        List<FieldError> errors
) {

    public record FieldError(String field, String message) {
    }

    /** Minimal envelope (used for the exact 401/403 payloads). */
    public static ProblemDetail minimal(int status, String title, String detail) {
        return new ProblemDetail("about:blank", title, status, detail, null, null, null);
    }

    /** Full envelope with instance + requestId (used for other error codes). */
    public static ProblemDetail full(int status, String title, String detail,
                                     String instance, String requestId) {
        return new ProblemDetail("about:blank", title, status, detail, instance, requestId, null);
    }

    /** Returns this envelope with the field-error array attached (omitted if empty). */
    public ProblemDetail withErrors(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            return this;
        }
        return new ProblemDetail(type, title, status, detail, instance, requestId, errors);
    }
}
