package com.ledgerpay.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> api(ApiException exception, HttpServletRequest request) {
        return respond(exception.errorCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorItem> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, "Request validation failed.", request, fields);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException exception, HttpServletRequest request) {
        if ("Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())) {
            return respond(
                    ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header is required.",
                    request,
                    null);
        }
        return respond(
                ErrorCode.VALIDATION_ERROR,
                "Required header is missing: " + exception.getHeaderName(),
                request,
                null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_JSON, "Request body is not valid JSON.", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> typeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return respond(
                ErrorCode.VALIDATION_ERROR,
                "Parameter '" + exception.getName() + "' is not valid.",
                request,
                null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> mediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json.", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "HTTP method is not supported for this path.", request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> missing(NoResourceFoundException exception, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, "Resource not found.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data integrity conflict on {}", request.getRequestURI());
        return respond(ErrorCode.DATA_CONFLICT, "The request conflicts with the current ledger state.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), exception);
        return respond(ErrorCode.INTERNAL_ERROR, "The request could not be completed.", request, null);
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode code, String message, HttpServletRequest request, List<FieldErrorItem> fields) {
        ErrorResponse body = ErrorResponses.of(clock, code, message, request.getRequestURI(), fields);
        return ResponseEntity.status(code.status()).body(body);
    }
}
