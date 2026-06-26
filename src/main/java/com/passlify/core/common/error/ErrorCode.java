package com.passlify.core.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable error codes surfaced in the {@code code} member of the
 * RFC-7807 problem response. Each maps to an HTTP status and a human title.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    BAD_SIGNATURE(HttpStatus.BAD_REQUEST, "Bad signature"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    INVALID_STATE(HttpStatus.CONFLICT, "Invalid state"),
    ALREADY_PUBLISHED(HttpStatus.CONFLICT, "Already published"),
    SOLD_OUT(HttpStatus.CONFLICT, "Sold out"),
    NOT_SELLABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Not sellable"),
    QTY_EXCEEDS_MAX(HttpStatus.UNPROCESSABLE_ENTITY, "Quantity exceeds maximum"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
