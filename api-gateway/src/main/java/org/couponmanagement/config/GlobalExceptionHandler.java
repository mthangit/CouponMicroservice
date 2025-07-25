package org.couponmanagement.config;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGrpcException(StatusRuntimeException e) {
        log.error("gRPC error: {}", e.getMessage(), e);

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Service unavailable");
        error.put("message", "Backend service error: " + e.getStatus().getDescription());
        error.put("code", e.getStatus().getCode().name());

        HttpStatus httpStatus = mapGrpcStatusToHttpStatus(e.getStatus().getCode());
        return ResponseEntity.status(httpStatus).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        log.error("Validation error: {}", e.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Validation failed");
        error.put("message", "Invalid request parameters");

        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError ->
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage())
        );
        error.put("fields", fieldErrors);

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Internal server error");
        error.put("message", "An unexpected error occurred");

        return ResponseEntity.internalServerError().body(error);
    }

    private HttpStatus mapGrpcStatusToHttpStatus(io.grpc.Status.Code grpcCode) {
        switch (grpcCode) {
            case OK:
                return HttpStatus.OK;
            case INVALID_ARGUMENT:
                return HttpStatus.BAD_REQUEST;
            case NOT_FOUND:
                return HttpStatus.NOT_FOUND;
            case PERMISSION_DENIED:
                return HttpStatus.FORBIDDEN;
            case UNAUTHENTICATED:
                return HttpStatus.UNAUTHORIZED;
            case UNAVAILABLE:
            case DEADLINE_EXCEEDED:
                return HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL:
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
