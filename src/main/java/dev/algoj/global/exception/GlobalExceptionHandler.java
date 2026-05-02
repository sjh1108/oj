package dev.algoj.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ErrorResponse.of(ec, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        BindingResult br = e.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = br.getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getRejectedValue() == null ? "" : fe.getRejectedValue().toString(),
                        fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException e) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.UNAUTHENTICATED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
