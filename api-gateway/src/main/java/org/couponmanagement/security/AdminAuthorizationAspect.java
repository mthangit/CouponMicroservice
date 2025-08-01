package org.couponmanagement.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * AOP Aspect to enforce admin authorization
 * Checks if user has admin role before allowing access to protected endpoints
 */
@Aspect
@Component
@Slf4j
public class AdminAuthorizationAspect {

    @Around("@annotation(requireAdmin)")
    public Object checkAdminAccess(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        
        // Get current HTTP request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.error("No HTTP request context available for admin check");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Request context not available");
        }
        
        HttpServletRequest request = attributes.getRequest();
        
        try {
            // Get authenticated user info
            JwtService.UserInfo userInfo = JwtAuthenticationFilter.getAuthenticatedUserInfo(request);
            
            if (userInfo == null) {
                log.warn("Admin access denied: No authenticated user found");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
            }
            
            // Check if user has admin role
            if (!userInfo.isAdmin()) {
                log.warn("Admin access denied: User {} (ID: {}) with role '{}' attempted to access admin endpoint: {}", 
                        userInfo.getUsername(), userInfo.getUserId(), userInfo.getRole(), 
                        request.getRequestURI());
                
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, requireAdmin.message());
            }
            
            // User is admin, allow access
            log.info("Admin access granted: User {} (ID: {}) accessing admin endpoint: {}", 
                    userInfo.getUsername(), userInfo.getUserId(), request.getRequestURI());
            
            return joinPoint.proceed();
            
        } catch (ResponseStatusException e) {
            // Re-throw HTTP status exceptions
            throw e;
        } catch (Exception e) {
            log.error("Error during admin authorization check: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Authorization check failed");
        }
    }
}
