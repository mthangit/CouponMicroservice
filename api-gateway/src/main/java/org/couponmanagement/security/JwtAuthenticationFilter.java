package org.couponmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    
    @Value("${security.jwt.header:Authorization}")
    private String jwtHeader;
    
    @Value("${security.jwt.prefix:Bearer }")
    private String jwtPrefix;
    
    @Value("#{'${security.public-endpoints}'.split(',')}")
    private List<String> publicEndpoints;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        
        log.debug("Processing request: {} {}", method, requestPath);
        
        if (isPublicEndpoint(requestPath)) {
            log.debug("Skipping authentication for public endpoint: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }
        
        if ("OPTIONS".equals(method)) {
            log.debug("Skipping authentication for OPTIONS request");
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String authHeader = request.getHeader(jwtHeader);
            
            if (authHeader == null || !authHeader.startsWith(jwtPrefix)) {
                log.debug("No valid JWT token found in request header");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
                return;
            }
            
            String token = authHeader.substring(jwtPrefix.length());
            
            if (!jwtService.validateToken(token)) {
                log.warn("Invalid JWT token provided");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
                return;
            }
            
            JwtService.UserInfo userInfo = jwtService.extractUserInfo(token);
            
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userInfo, 
                            null, 
                            new ArrayList<>() // No authorities for now
                    );
            
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            request.setAttribute("userId", userInfo.getUserId());
            request.setAttribute("username", userInfo.getUsername());
            request.setAttribute("userInfo", userInfo);
            
            log.debug("Successfully authenticated user: {} (ID: {})", 
                    userInfo.getUsername(), userInfo.getUserId());
            
        } catch (JwtService.JwtValidationException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token validation failed: " + e.getMessage() + "\"}");
            return;
        } catch (Exception e) {
            log.error("Unexpected error during JWT authentication: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Authentication error\"}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isPublicEndpoint(String requestPath) {
        return publicEndpoints.stream()
                .anyMatch(endpoint -> {
                    // Handle wildcard patterns
                    if (endpoint.endsWith("/**")) {
                        String basePath = endpoint.substring(0, endpoint.length() - 3);
                        return requestPath.startsWith(basePath);
                    } else if (endpoint.endsWith("/*")) {
                        String basePath = endpoint.substring(0, endpoint.length() - 2);
                        return requestPath.startsWith(basePath) && 
                               requestPath.indexOf('/', basePath.length()) == -1;
                    } else {
                        return requestPath.equals(endpoint);
                    }
                });
    }

    public static JwtService.UserInfo getAuthenticatedUserInfo(HttpServletRequest request) {
        Object userInfo = request.getAttribute("userInfo");
        return userInfo instanceof JwtService.UserInfo ? (JwtService.UserInfo) userInfo : null;
    }
}
