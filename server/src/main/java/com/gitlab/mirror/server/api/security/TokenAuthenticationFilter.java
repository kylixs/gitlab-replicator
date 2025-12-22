package com.gitlab.mirror.server.api.security;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
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
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final GitLabMirrorProperties properties;

    public TokenAuthenticationFilter(GitLabMirrorProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for health check and public endpoints
        if (path.startsWith("/actuator") || path.startsWith("/swagger") ||
            path.startsWith("/v3/api-docs") || path.startsWith("/api/status")) {
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
        if (!isValidToken(token)) {
            sendUnauthorizedResponse(response, "Invalid token");
            return;
        }

        // Token is valid, continue
        filterChain.doFilter(request, response);
    }

    private boolean isValidToken(String token) {
        // Validate against independent API key (not GitLab tokens)
        String apiKey = properties.getApi().getKey();
        return apiKey != null && apiKey.equals(token);
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
