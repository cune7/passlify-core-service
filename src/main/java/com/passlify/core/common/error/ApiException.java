package com.passlify.core.common.error;

/**
 * Single typed exception carrying an {@link ErrorCode}. Use the static factories
 * at call sites for readability; the {@link GlobalExceptionHandler} renders them
 * as RFC-7807 problem responses.
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(ErrorCode.NOT_FOUND, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(ErrorCode.CONFLICT, detail);
    }

    public static ApiException invalidState(String detail) {
        return new ApiException(ErrorCode.INVALID_STATE, detail);
    }

    public static ApiException validation(String detail) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, detail);
    }

    public static ApiException of(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }
}
