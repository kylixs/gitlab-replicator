package com.gitlab.mirror.server.api.security;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.User;
import com.gitlab.mirror.server.service.auth.AuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Token Authentication Filter
 * <p>
 * Validates Bearer token in Authorization header
 * Supports both:
 * - API Key authentication (legacy, for admin operations)
 * - User token authentication (for logged-in users)
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final GitLabMirrorProperties properties;
    private final AuthenticationService authenticationService;

    public TokenAuthenticationFilter(GitLabMirrorProperties properties,
                                     AuthenticationService authenticationService) {
        this.properties = properties;
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for health check, public endpoints, auth APIs, and webhooks
        if (path.startsWith("/actuator") || path.startsWith("/swagger") ||
            path.startsWith("/v3/api-docs") || path.startsWith("/api/status") ||
            path.startsWith("/api/auth/") || path.startsWith("/api/webhooks/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract and validate token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        // Try API Key authentication first (backward compatibility)
        if (isValidApiKey(token)) {
            // Create virtual admin user for API Key access
            User virtualAdmin = new User();
            virtualAdmin.setId(-1L); // Special ID for API Key user
            virtualAdmin.setUsername("api_key");
            virtualAdmin.setDisplayName("API Key User");
            virtualAdmin.setEnabled(true);

            request.setAttribute("currentUser", virtualAdmin);
            request.setAttribute("authType", "api_key");

            filterChain.doFilter(request, response);
            return;
        }

        // Try user token authentication
        User user = authenticationService.validateToken(token);
        if (user != null) {
            // Store user info in request for downstream use
            request.setAttribute("currentUser", user);
            request.setAttribute("authType", "user_token");

            filterChain.doFilter(request, response);
            return;
        }

        // Both authentication methods failed
        sendUnauthorizedResponse(response, "Invalid or expired token");
    }

    /**
     * Validate API Key (legacy authentication)
     */
    private boolean isValidApiKey(String token) {
        String apiKey = properties.getApi().getKey();
        return apiKey != null && apiKey.equals(token);
    }

    /**
     * Extract client IP address considering proxy headers
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // Take first IP if multiple
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                ip = ip.substring(0, commaIndex).trim();
            }
            return ip;
        }

        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        return request.getRemoteAddr();
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"%s\"}}",
                message
        ));
    }
}
