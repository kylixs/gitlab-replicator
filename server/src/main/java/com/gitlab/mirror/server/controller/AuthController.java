package com.gitlab.mirror.server.controller;

import com.gitlab.mirror.common.model.auth.*;
import com.gitlab.mirror.server.entity.AuthToken;
import com.gitlab.mirror.server.entity.User;
import com.gitlab.mirror.server.mapper.AuthTokenMapper;
import com.gitlab.mirror.server.service.auth.AuthenticationService;
import com.gitlab.mirror.server.service.auth.exception.AccountLockedException;
import com.gitlab.mirror.server.service.auth.exception.AuthenticationException;
import com.gitlab.mirror.server.service.auth.model.ChallengeInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Authentication Controller
 * <p>
 * REST API endpoints for authentication operations
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication API endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AuthTokenMapper authTokenMapper;

    /**
     * Get authentication challenge
     *
     * @param request Challenge request
     * @return Challenge response with salt and iterations
     */
    @PostMapping("/challenge")
    @Operation(summary = "Get authentication challenge", description = "Generate SCRAM challenge for authentication")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge generated successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<com.gitlab.mirror.common.model.auth.ApiResponse<ChallengeResponse>> getChallenge(
            @Valid @RequestBody ChallengeRequest request) {

        try {
            ChallengeInfo challengeInfo = authenticationService.generateChallenge(request.getUsername());

            ChallengeResponse response = ChallengeResponse.builder()
                    .challenge(challengeInfo.getChallenge())
                    .salt(challengeInfo.getSalt())
                    .iterations(challengeInfo.getIterations())
                    .expiresAt(challengeInfo.getExpiresAt())
                    .build();

            return ResponseEntity.ok(com.gitlab.mirror.common.model.auth.ApiResponse.success(response));

        } catch (AuthenticationException e) {
            // User not found or disabled - return 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(com.gitlab.mirror.common.model.auth.ApiResponse.error("USER_NOT_FOUND", e.getMessage()));
        }
    }

    /**
     * Login with SCRAM authentication
     *
     * @param request     Login request
     * @param httpRequest HTTP request for extracting IP and User-Agent
     * @return Login response with token
     */
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with SCRAM-SHA-256 and obtain access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Authentication failed"),
            @ApiResponse(responseCode = "423", description = "Account locked due to too many failed attempts")
    })
    public ResponseEntity<com.gitlab.mirror.common.model.auth.ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        try {
            // Authenticate and get token
            String token = authenticationService.login(
                    request.getUsername(),
                    request.getChallenge(),
                    request.getClientProof(),
                    ip,
                    userAgent
            );

            // Get token info
            AuthToken authToken = authTokenMapper.selectByToken(token);
            User user = authenticationService.validateToken(token);

            // Build response
            LoginResponse response = LoginResponse.builder()
                    .token(token)
                    .expiresAt(authToken.getExpiresAt())
                    .user(UserInfo.builder()
                            .username(user.getUsername())
                            .displayName(user.getDisplayName())
                            .build())
                    .build();

            return ResponseEntity.ok(com.gitlab.mirror.common.model.auth.ApiResponse.success(response));

        } catch (AccountLockedException e) {
            // Account locked - return 423 (Locked)
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(com.gitlab.mirror.common.model.auth.ApiResponse.accountLocked(e.getLockoutSeconds(), e.getFailureCount()));

        } catch (AuthenticationException e) {
            // Authentication failed - return 401
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(com.gitlab.mirror.common.model.auth.ApiResponse.error("AUTHENTICATION_ERROR", e.getMessage()));
        }
    }

    /**
     * Logout
     *
     * @param httpRequest HTTP request for extracting token
     * @return Success response
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Logout and invalidate current token")
    @SecurityRequirement(name = "Bearer Token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful")
    })
    public ResponseEntity<com.gitlab.mirror.common.model.auth.ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        String token = extractToken(httpRequest);

        if (token != null) {
            authenticationService.logout(token);
            log.debug("User logged out successfully");
        }

        return ResponseEntity.ok(com.gitlab.mirror.common.model.auth.ApiResponse.success());
    }

    /**
     * Verify token
     *
     * @param httpRequest HTTP request for extracting token
     * @return Token verification response
     */
    @GetMapping("/verify")
    @Operation(summary = "Verify token", description = "Verify if current token is valid")
    @SecurityRequirement(name = "Bearer Token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    })
    public ResponseEntity<com.gitlab.mirror.common.model.auth.ApiResponse<TokenVerifyResponse>> verifyToken(HttpServletRequest httpRequest) {
        String token = extractToken(httpRequest);

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(com.gitlab.mirror.common.model.auth.ApiResponse.error("MISSING_TOKEN", "Missing authentication token"));
        }

        User user = authenticationService.validateToken(token);

        if (user == null) {
            TokenVerifyResponse response = TokenVerifyResponse.builder()
                    .valid(false)
                    .build();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(com.gitlab.mirror.common.model.auth.ApiResponse.error("INVALID_TOKEN", "Token is invalid or expired"));
        }

        // Token is valid
        AuthToken authToken = authTokenMapper.selectByToken(token);

        TokenVerifyResponse response = TokenVerifyResponse.builder()
                .valid(true)
                .expiresAt(authToken.getExpiresAt())
                .user(UserInfo.builder()
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .build())
                .build();

        return ResponseEntity.ok(com.gitlab.mirror.common.model.auth.ApiResponse.success(response));
    }

    /**
     * Extract client IP address considering proxy headers
     *
     * @param request HTTP request
     * @return Client IP address
     */
    private String getClientIp(HttpServletRequest request) {
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

    /**
     * Extract Bearer token from Authorization header
     *
     * @param request HTTP request
     * @return Token string, or null if not present
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
