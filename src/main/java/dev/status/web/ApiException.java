package dev.status.web;

import org.springframework.http.HttpStatus;

/**
 * Sealed hierarchy of application-level HTTP errors, mapped to RFC 9457
 * envelopes by {@link GlobalExceptionHandler}.
 */
public sealed abstract class ApiException extends RuntimeException
        permits ApiException.BadRequest,
                ApiException.Forbidden,
                ApiException.NotFound,
                ApiException.Conflict,
                ApiException.Unprocessable {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public static ApiException notFound(String detail) {
        return new NotFound(detail);
    }

    public static ApiException conflict(String detail) {
        return new Conflict(detail);
    }

    public static ApiException forbidden(String detail) {
        return new Forbidden(detail);
    }

    public static ApiException badRequest(String detail) {
        return new BadRequest(detail);
    }

    public static ApiException unprocessable(String detail) {
        return new Unprocessable(detail);
    }

    public static final class BadRequest extends ApiException {
        public BadRequest(String detail) {
            super(HttpStatus.BAD_REQUEST, "Bad Request", detail);
        }
    }

    public static final class Forbidden extends ApiException {
        public Forbidden(String detail) {
            super(HttpStatus.FORBIDDEN, "Forbidden", detail);
        }
    }

    public static final class NotFound extends ApiException {
        public NotFound(String detail) {
            super(HttpStatus.NOT_FOUND, "Not Found", detail);
        }
    }

    public static final class Conflict extends ApiException {
        public Conflict(String detail) {
            super(HttpStatus.CONFLICT, "Conflict", detail);
        }
    }

    public static final class Unprocessable extends ApiException {
        public Unprocessable(String detail) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", detail);
        }
    }
}
