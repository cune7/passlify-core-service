package com.passlify.core.common.error;

import java.net.URI;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to RFC-7807 {@code application/problem+json}. Never leaks stack
 * traces. The {@code code} property is the stable machine-readable {@link ErrorCode}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_BASE = "https://passlify.rs/errors/";

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        return problem(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(ErrorCode.VALIDATION_ERROR, detail.isBlank() ? "Validation failed" : detail);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail handleAccessDenied(RuntimeException ex) {
        return problem(ErrorCode.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Detail is deliberately generic; the real cause goes to logs, not the client.
        return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        HttpStatus status = code.status();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(code.title());
        pd.setType(URI.create(TYPE_BASE + code.name().toLowerCase().replace('_', '-')));
        pd.setProperty("code", code.name());
        return pd;
    }
}
