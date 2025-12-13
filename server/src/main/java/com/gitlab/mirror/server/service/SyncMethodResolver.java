package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Sync Method Resolver
 * <p>
 * Resolves the appropriate sync method for a given project path
 * based on configuration rules.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncMethodResolver {

    private final GitLabMirrorProperties properties;

    /**
     * Resolve sync method for a project path
     *
     * @param projectPath Project path (e.g., "group1/subgroup/project")
     * @return Sync method: "push_mirror" or "pull_sync"
     */
    public String resolveSyncMethod(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) {
            log.warn("Empty project path, using default sync method");
            return getDefaultSyncMethod();
        }

        // Check specific group path configurations (higher priority)
        for (GitLabMirrorProperties.SyncMethodConfig config : properties.getSync().getSyncMethods()) {
            if (matchesPattern(projectPath, config.getGroupPath())) {
                log.debug("Project '{}' matches pattern '{}', using sync method: {}",
                    projectPath, config.getGroupPath(), config.getMethod());
                return config.getMethod();
            }
        }

        // Fall back to default
        String defaultMethod = getDefaultSyncMethod();
        log.debug("Project '{}' using default sync method: {}", projectPath, defaultMethod);
        return defaultMethod;
    }

    /**
     * Get default sync method
     *
     * @return Default sync method
     */
    public String getDefaultSyncMethod() {
        return properties.getSync().getDefaultSyncMethod();
    }

    /**
     * Check if project path matches the pattern
     * <p>
     * Supports wildcard patterns:
     * - "group/*" matches "group/project1", "group/project2"
     * - "group/sub/*" matches "group/sub/project1"
     * - "group" matches exactly "group"
     *
     * @param projectPath Project path
     * @param pattern     Pattern (may contain wildcards)
     * @return true if matches
     */
    private boolean matchesPattern(String projectPath, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // Exact match
        if (pattern.equals(projectPath)) {
            return true;
        }

        // Wildcard pattern
        if (pattern.contains("*")) {
            // Convert wildcard pattern to regex
            // "group/*" -> "^group/[^/]+$"
            // "group/sub/*" -> "^group/sub/[^/]+$"
            // "group/**" -> "^group/.*$"
            String regexPattern = pattern
                .replace(".", "\\.")  // Escape dots
                .replace("**", "DOUBLE_STAR")  // Temporary placeholder
                .replace("*", "[^/]+")  // Single * matches one level
                .replace("DOUBLE_STAR", ".*");  // ** matches any levels

            // Ensure pattern matches from start to end
            if (!regexPattern.startsWith("^")) {
                regexPattern = "^" + regexPattern;
            }
            if (!regexPattern.endsWith("$")) {
                regexPattern = regexPattern + "$";
            }

            Pattern regex = Pattern.compile(regexPattern);
            return regex.matcher(projectPath).matches();
        }

        return false;
    }

    /**
     * Check if sync method is pull_sync
     *
     * @param syncMethod Sync method string
     * @return true if pull_sync
     */
    public boolean isPullSync(String syncMethod) {
        return "pull_sync".equalsIgnoreCase(syncMethod);
    }

    /**
     * Check if sync method is push_mirror
     *
     * @param syncMethod Sync method string
     * @return true if push_mirror
     */
    public boolean isPushMirror(String syncMethod) {
        return "push_mirror".equalsIgnoreCase(syncMethod);
    }
}
