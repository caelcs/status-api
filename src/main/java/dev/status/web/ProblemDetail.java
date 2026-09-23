package dev.status.web;

/**
 * RFC 9457 problem-details envelope builder. Field ordering is deterministic
 * and optional fields are only present when provided.
 */
public final class ProblemDetail {

    private ProblemDetail() {
    }

    /** Minimal envelope (used for the exact 401/403 payloads). */
    public static java.util.Map<String, Object> minimal(int status, String title, String detail) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        return body;
    }

    /** Full envelope with instance + requestId (used for other error codes). */
    public static java.util.Map<String, Object> full(int status, String title, String detail,
                                                     String instance, String requestId) {
        java.util.Map<String, Object> body = minimal(status, title, detail);
        body.put("instance", instance);
        body.put("requestId", requestId);
        return body;
    }
}
