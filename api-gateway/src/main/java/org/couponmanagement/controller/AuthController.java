package org.couponmanagement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for JWT token generation and validation
 * This is for testing purposes - in production, authentication would be handled by a separate service
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final JwtService jwtService;
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for user: {}", request.getUsername());
        
        try {
            Integer userId = getUserIdByUsername(request.getUsername());
            String userRole = getUserRole(request.getUsername());

            if (userId == null) {
                return ResponseEntity.badRequest()
                        .body(LoginResponse.failure("Invalid username or password"));
            }

            String token = jwtService.generateToken(userId, request.getUsername(), userRole);
            
            log.info("Successfully generated token for user: {} (ID: {})", request.getUsername(), userId);
            
            return ResponseEntity.ok(LoginResponse.success(token, userId, request.getUsername(), userRole));
            
        } catch (Exception e) {
            log.error("Error during login for user: {}, error: {}", request.getUsername(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(LoginResponse.failure("Internal server error"));
        }
    }
    
    /**
     * Validate JWT token
     * POST /api/v1/auth/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidateTokenResponse> validateToken(@Valid @RequestBody ValidateTokenRequest request) {
        log.info("Token validation request");
        
        try {
            boolean isValid = jwtService.validateToken(request.getToken());
            
            if (isValid) {
                JwtService.UserInfo userInfo = jwtService.extractUserInfo(request.getToken());
                
                log.info("Token is valid for user: {} (ID: {})", userInfo.getUsername(), userInfo.getUserId());
                
                return ResponseEntity.ok(ValidateTokenResponse.success(userInfo.getUserId(), userInfo.getUsername()));
            } else {
                log.warn("Invalid token provided");
                return ResponseEntity.badRequest()
                        .body(ValidateTokenResponse.failure("Invalid or expired token"));
            }
            
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ValidateTokenResponse.failure("Token validation failed: " + e.getMessage()));
        }
    }
    
    /**
     * Get user profile from JWT token
     * GET /api/v1/auth/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getUserProfile(@RequestHeader("Authorization") String authHeader) {
        log.info("Get user profile request");
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                        .body(UserProfileResponse.failure("Missing or invalid Authorization header"));
            }
            
            String token = authHeader.substring("Bearer ".length());
            
            if (!jwtService.validateToken(token)) {
                return ResponseEntity.badRequest()
                        .body(UserProfileResponse.failure("Invalid or expired token"));
            }
            
            JwtService.UserInfo userInfo = jwtService.extractUserInfo(token);
            
            log.info("Retrieved profile for user: {} (ID: {})", userInfo.getUsername(), userInfo.getUserId());
            
            return ResponseEntity.ok(UserProfileResponse.success(userInfo.getUserId(), userInfo.getUsername()));
            
        } catch (Exception e) {
            log.error("Error getting user profile: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(UserProfileResponse.failure("Failed to get user profile: " + e.getMessage()));
        }
    }
    
    private Integer getUserIdByUsername(String username) {
        return switch (username.toLowerCase()) {
            case "user1", "john", "test1" -> 1;
            case "user2", "jane", "test2" -> 2;
            case "user3", "test3" -> 3;
            case "admin", "administrator" -> 9001; // Admin users
            case "demo", "demo1" -> 4;
            case "test", "testuser" -> 5;
            default -> null;
        };
    }

    private String getUserRole(String username) {
        return switch (username.toLowerCase()) {
            case "admin", "administrator" -> "ADMIN";
            default -> "USER";
        };
    }
    
    // ============ DTOs ============
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LoginRequest {
        @jakarta.validation.constraints.NotBlank(message = "Username is required")
        private String username;
        
        @jakarta.validation.constraints.NotBlank(message = "Password is required")
        private String password;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class LoginResponse {
        private boolean success;
        private String token;
        private Integer userId;
        private String username;
        private String role;
        private String message;
        private String errorMessage;

        public static LoginResponse success(String token, Integer userId, String username, String role) {
            return LoginResponse.builder()
                    .success(true)
                    .token(token)
                    .userId(userId)
                    .username(username)
                    .role(role)
                    .message("Login successful")
                    .build();
        }
        
        public static LoginResponse failure(String errorMessage) {
            return LoginResponse.builder()
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidateTokenRequest {
        @jakarta.validation.constraints.NotBlank(message = "Token is required")
        private String token;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidateTokenResponse {
        private boolean valid;
        private Integer userId;
        private String username;
        private String message;
        private String errorMessage;
        
        public static ValidateTokenResponse success(Integer userId, String username) {
            return ValidateTokenResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .username(username)
                    .message("Token is valid")
                    .build();
        }
        
        public static ValidateTokenResponse failure(String errorMessage) {
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserProfileResponse {
        private boolean success;
        private Integer userId;
        private String username;
        private String message;
        private String errorMessage;
        
        public static UserProfileResponse success(Integer userId, String username) {
            return UserProfileResponse.builder()
                    .success(true)
                    .userId(userId)
                    .username(username)
                    .message("Profile retrieved successfully")
                    .build();
        }
        
        public static UserProfileResponse failure(String errorMessage) {
            return UserProfileResponse.builder()
                    .success(false)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}
