package org.couponmanagement.config;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.performance.ErrorMetricsRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Autowired
    private ErrorMetricsRegistry errorMetricsRegistry;

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGrpcException(StatusRuntimeException e) {
        log.error("gRPC error: {}", e.getMessage(), e);

        errorMetricsRegistry.incrementGrpcStatusCode(e.getStatus().getCode().name(), "api-gateway", "grpc-call");
        errorMetricsRegistry.incrementErrorCode("GRPC_ERROR", "api-gateway", "grpc-call");

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Service unavailable");
        error.put("message", "Backend service error: " + e.getStatus().getDescription());
        error.put("code", e.getStatus().getCode().name());

        HttpStatus httpStatus = mapGrpcStatusToHttpStatus(e.getStatus().getCode());
        
        errorMetricsRegistry.incrementHttpStatusCode(httpStatus.value(), "grpc-exception");
        
        return ResponseEntity.status(httpStatus).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage());

        // Track validation error metrics
        e.getBindingResult().getFieldErrors().forEach(fieldError -> {
            errorMetricsRegistry.incrementValidationError(fieldError.getField(), "validation");
            errorMetricsRegistry.incrementErrorCode("VALIDATION_ERROR", "api-gateway", "validation");
        });

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Validation failed");
        error.put("message", "Invalid request parameters");

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError ->
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        error.put("fields", fieldErrors);

        errorMetricsRegistry.incrementHttpStatusCode(400, "validation-exception");

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        errorMetricsRegistry.incrementErrorCode("INTERNAL_ERROR", "api-gateway", "generic-exception");
        errorMetricsRegistry.incrementHttpStatusCode(500, "generic-exception");

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Internal server error");
        error.put("message", "An unexpected error occurred");

        return ResponseEntity.internalServerError().body(error);
    }

    private HttpStatus mapGrpcStatusToHttpStatus(io.grpc.Status.Code grpcCode) {
        return switch (grpcCode) {
            case OK -> HttpStatus.OK;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case UNAVAILABLE, DEADLINE_EXCEEDED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
