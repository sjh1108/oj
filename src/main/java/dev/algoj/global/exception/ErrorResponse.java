package dev.algoj.global.exception;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        LocalDateTime timestamp,
        List<FieldError> fieldErrors
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                List.of()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(
                errorCode.getCode(),
                message,
                LocalDateTime.now(),
                List.of()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage(),
                LocalDateTime.now(),
                fieldErrors
        );
    }

    public record FieldError(String field, String value, String reason) {}
}
