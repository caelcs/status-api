package dev.status.domain;

/**
 * Per-service health status. Wire value is lowercase (api-contract §3.1).
 */
public enum Status {
    UP("up"),
    DEGRADED("degraded"),
    DOWN("down"),
    UNKNOWN("unknown");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** Case-insensitive parse; null/blank/unknown-miss maps to {@link #UNKNOWN}. */
    public static Status fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        for (Status s : values()) {
            if (s.value.equalsIgnoreCase(raw) || s.name().equalsIgnoreCase(raw)) {
                return s;
            }
        }
        return UNKNOWN;
    }

    /** Strict check for query-param validation (invalid value -> 400). */
    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (Status s : values()) {
            if (s.value.equalsIgnoreCase(raw) || s.name().equalsIgnoreCase(raw)) {
                return true;
            }
        }
        return false;
    }
}
