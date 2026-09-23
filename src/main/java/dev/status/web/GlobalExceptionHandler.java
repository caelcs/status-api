package dev.status.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ProblemDetail body;
        if (status == HttpStatus.FORBIDDEN || status == HttpStatus.UNAUTHORIZED) {
            // exact 4-field payloads per api-contract §2.1
            body = ProblemDetail.minimal(status.value(), ex.getTitle(), ex.getMessage());
        } else {
            body = ProblemDetail.full(status.value(), ex.getTitle(), ex.getMessage(),
                    request.getRequestURI(), RequestIdFilter.currentId());
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ProblemDetail.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ProblemDetail.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        return badRequest(request, "Validation failed", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<ProblemDetail.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ProblemDetail.FieldError(
                        lastSegment(v.getPropertyPath() == null ? "request" : v.getPropertyPath().toString()),
                        v.getMessage() == null ? "invalid" : v.getMessage()))
                .toList();
        return badRequest(request, "Validation failed", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException ex,
                                                                HttpServletRequest request) {
        List<ProblemDetail.FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(re -> new ProblemDetail.FieldError(
                        "request",
                        re.getDefaultMessage() == null ? "invalid" : re.getDefaultMessage()))
                .toList();
        return badRequest(request, "Validation failed", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return badRequest(request, "Malformed request body", List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> handleTypeMismatch(Exception ex, HttpServletRequest request) {
        return badRequest(request, "Invalid request parameter", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.full(404, "Not Found", "Resource not found",
                request.getRequestURI(), RequestIdFilter.currentId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail body = ProblemDetail.full(500, "Internal Server Error", "Internal Server Error",
                request.getRequestURI(), RequestIdFilter.currentId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private ResponseEntity<ProblemDetail> badRequest(HttpServletRequest request, String detail,
                                                     List<ProblemDetail.FieldError> errors) {
        ProblemDetail body = ProblemDetail.full(400, "Bad Request", detail,
                request.getRequestURI(), RequestIdFilter.currentId())
                .withErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static String lastSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
