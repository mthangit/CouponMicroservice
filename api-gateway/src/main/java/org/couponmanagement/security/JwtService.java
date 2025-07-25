package org.couponmanagement.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private Long jwtExpiration;
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
    
    /**
     * Extract user ID from JWT token
     */
    public Integer extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Object userIdObj = claims.get("userId");

            switch (userIdObj) {
                case null -> {
                    log.warn("No userId found in JWT token");
                    return null;
                }
                case Integer i -> {
                    return i;
                }
                case Number number -> {
                    return number.intValue();
                }
                case String s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        log.warn("Cannot parse userId from string: {}", userIdObj);
                        return null;
                    }
                }
                default -> {
                }
            }

            log.warn("Unexpected userId type in JWT: {}", userIdObj.getClass());
            return null;
            
        } catch (Exception e) {
            log.error("Error extracting userId from JWT: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract username from JWT token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    /**
     * Extract expiration date from JWT token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    /**
     * Extract specific claim from JWT token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    /**
     * Extract all claims from JWT token
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            throw new JwtValidationException("Token is expired", e);
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
            throw new JwtValidationException("Token is unsupported", e);
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
            throw new JwtValidationException("Token is malformed", e);
        } catch (SecurityException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            throw new JwtValidationException("Token signature is invalid", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null: {}", e.getMessage());
            throw new JwtValidationException("Token is empty or null", e);
        }
    }
    
    /**
     * Check if JWT token is expired
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (JwtValidationException e) {
            return true; // Consider invalid tokens as expired
        }
    }
    
    /**
     * Validate JWT token
     */
    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtValidationException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    public String generateToken(Integer userId, String username) {
        return generateToken(userId, username, "USER");
    }

    public String generateToken(Integer userId, String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration * 1000))
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * Extract user information from JWT token
     */
    public UserInfo extractUserInfo(String token) {
        try {
            Claims claims = extractAllClaims(token);
            
            Integer userId = extractUserId(token);
            String username = claims.getSubject();
            
            if (userId == null) {
                throw new JwtValidationException("No userId found in token");
            }
            
            String role = claims.get("role", String.class);
            if (role == null) {
                role = "USER"; // Default role
            }

            return UserInfo.builder()
                    .userId(userId)
                    .username(username)
                    .role(role)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error extracting user info from JWT: {}", e.getMessage());
            throw new JwtValidationException("Cannot extract user info from token", e);
        }
    }
    
    /**
     * User information extracted from JWT
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserInfo {
        private Integer userId;
        private String username;
        private String role;

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }
    
    /**
     * Custom exception for JWT validation errors
     */
    public static class JwtValidationException extends RuntimeException {
        public JwtValidationException(String message) {
            super(message);
        }
        
        public JwtValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
