package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sync Method Resolver Test
 * <p>
 * MUST strictly perform unit testing for sync method resolution
 *
 * @author GitLab Mirror Team
 */
class SyncMethodResolverTest {

    private SyncMethodResolver resolver;
    private GitLabMirrorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new GitLabMirrorProperties();
        properties.setSync(new GitLabMirrorProperties.SyncConfig());
        resolver = new SyncMethodResolver(properties);
    }

    @Test
    void testDefaultSyncMethod() {
        // Default is push_mirror
        String method = resolver.resolveSyncMethod("any/project");

        assertThat(method).isEqualTo("push_mirror");
    }

    @Test
    void testCustomDefaultSyncMethod() {
        // Set default to pull_sync
        properties.getSync().setDefaultSyncMethod("pull_sync");

        String method = resolver.resolveSyncMethod("any/project");

        assertThat(method).isEqualTo("pull_sync");
    }

    @Test
    void testExactGroupPathMatch() {
        // Configure specific group
        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();
        GitLabMirrorProperties.SyncMethodConfig config = new GitLabMirrorProperties.SyncMethodConfig();
        config.setGroupPath("critical/important");
        config.setMethod("pull_sync");
        configs.add(config);
        properties.getSync().setSyncMethods(configs);

        // Exact match
        String method = resolver.resolveSyncMethod("critical/important");
        assertThat(method).isEqualTo("pull_sync");

        // No match - use default
        method = resolver.resolveSyncMethod("critical/other");
        assertThat(method).isEqualTo("push_mirror");
    }

    @Test
    void testWildcardPatternMatch() {
        // Configure wildcard pattern
        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();
        GitLabMirrorProperties.SyncMethodConfig config = new GitLabMirrorProperties.SyncMethodConfig();
        config.setGroupPath("critical/*");
        config.setMethod("pull_sync");
        configs.add(config);
        properties.getSync().setSyncMethods(configs);

        // Should match
        String method = resolver.resolveSyncMethod("critical/project1");
        assertThat(method).isEqualTo("pull_sync");

        method = resolver.resolveSyncMethod("critical/project2");
        assertThat(method).isEqualTo("pull_sync");

        // Should not match (not direct child)
        method = resolver.resolveSyncMethod("critical/sub/project");
        assertThat(method).isEqualTo("push_mirror");

        // Should not match (different group)
        method = resolver.resolveSyncMethod("normal/project");
        assertThat(method).isEqualTo("push_mirror");
    }

    @Test
    void testDoubleWildcardPattern() {
        // Configure double wildcard pattern
        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();
        GitLabMirrorProperties.SyncMethodConfig config = new GitLabMirrorProperties.SyncMethodConfig();
        config.setGroupPath("critical/**");
        config.setMethod("pull_sync");
        configs.add(config);
        properties.getSync().setSyncMethods(configs);

        // Should match all levels
        String method = resolver.resolveSyncMethod("critical/project1");
        assertThat(method).isEqualTo("pull_sync");

        method = resolver.resolveSyncMethod("critical/sub/project");
        assertThat(method).isEqualTo("pull_sync");

        method = resolver.resolveSyncMethod("critical/sub/deep/project");
        assertThat(method).isEqualTo("pull_sync");

        // Should not match
        method = resolver.resolveSyncMethod("normal/project");
        assertThat(method).isEqualTo("push_mirror");
    }

    @Test
    void testMultipleConfigurationsPriorityOrder() {
        // Configure multiple patterns - first match wins
        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();

        // More specific pattern first
        GitLabMirrorProperties.SyncMethodConfig config1 = new GitLabMirrorProperties.SyncMethodConfig();
        config1.setGroupPath("test/specific");
        config1.setMethod("pull_sync");
        configs.add(config1);

        // Less specific pattern
        GitLabMirrorProperties.SyncMethodConfig config2 = new GitLabMirrorProperties.SyncMethodConfig();
        config2.setGroupPath("test/*");
        config2.setMethod("push_mirror");
        configs.add(config2);

        properties.getSync().setSyncMethods(configs);

        // Exact match should win (first in list)
        String method = resolver.resolveSyncMethod("test/specific");
        assertThat(method).isEqualTo("pull_sync");

        // Wildcard match
        method = resolver.resolveSyncMethod("test/other");
        assertThat(method).isEqualTo("push_mirror");
    }

    @Test
    void testComplexScenario() {
        // Real-world configuration
        properties.getSync().setDefaultSyncMethod("push_mirror");

        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();

        // Critical projects use pull_sync
        GitLabMirrorProperties.SyncMethodConfig critical = new GitLabMirrorProperties.SyncMethodConfig();
        critical.setGroupPath("critical/*");
        critical.setMethod("pull_sync");
        configs.add(critical);

        // DevOps team uses pull_sync
        GitLabMirrorProperties.SyncMethodConfig devops = new GitLabMirrorProperties.SyncMethodConfig();
        devops.setGroupPath("devops/**");
        devops.setMethod("pull_sync");
        configs.add(devops);

        properties.getSync().setSyncMethods(configs);

        // Test various paths
        assertThat(resolver.resolveSyncMethod("critical/api")).isEqualTo("pull_sync");
        assertThat(resolver.resolveSyncMethod("devops/tools/ci")).isEqualTo("pull_sync");
        assertThat(resolver.resolveSyncMethod("devops/infrastructure")).isEqualTo("pull_sync");
        assertThat(resolver.resolveSyncMethod("normal/app")).isEqualTo("push_mirror");
        assertThat(resolver.resolveSyncMethod("backend/service")).isEqualTo("push_mirror");
    }

    @Test
    void testEmptyOrNullProjectPath() {
        // Empty path
        String method = resolver.resolveSyncMethod("");
        assertThat(method).isEqualTo("push_mirror");

        // Null path
        method = resolver.resolveSyncMethod(null);
        assertThat(method).isEqualTo("push_mirror");
    }

    @Test
    void testIsPullSync() {
        assertThat(resolver.isPullSync("pull_sync")).isTrue();
        assertThat(resolver.isPullSync("PULL_SYNC")).isTrue();
        assertThat(resolver.isPullSync("push_mirror")).isFalse();
        assertThat(resolver.isPullSync("unknown")).isFalse();
    }

    @Test
    void testIsPushMirror() {
        assertThat(resolver.isPushMirror("push_mirror")).isTrue();
        assertThat(resolver.isPushMirror("PUSH_MIRROR")).isTrue();
        assertThat(resolver.isPushMirror("pull_sync")).isFalse();
        assertThat(resolver.isPushMirror("unknown")).isFalse();
    }

    @Test
    void testGetDefaultSyncMethod() {
        assertThat(resolver.getDefaultSyncMethod()).isEqualTo("push_mirror");

        properties.getSync().setDefaultSyncMethod("pull_sync");
        assertThat(resolver.getDefaultSyncMethod()).isEqualTo("pull_sync");
    }

    @Test
    void testPatternWithDots() {
        // Test pattern matching with dots in project names
        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();
        GitLabMirrorProperties.SyncMethodConfig config = new GitLabMirrorProperties.SyncMethodConfig();
        config.setGroupPath("com.example/*");
        config.setMethod("pull_sync");
        configs.add(config);
        properties.getSync().setSyncMethods(configs);

        String method = resolver.resolveSyncMethod("com.example/project");
        assertThat(method).isEqualTo("pull_sync");
    }

    @Test
    void testNestedGroupPattern() {
        // Test nested group patterns
        List<GitLabMirrorProperties.SyncMethodConfig> configs = new ArrayList<>();
        GitLabMirrorProperties.SyncMethodConfig config = new GitLabMirrorProperties.SyncMethodConfig();
        config.setGroupPath("company/department/team/*");
        config.setMethod("pull_sync");
        configs.add(config);
        properties.getSync().setSyncMethods(configs);

        // Should match
        String method = resolver.resolveSyncMethod("company/department/team/project1");
        assertThat(method).isEqualTo("pull_sync");

        // Should not match (one level deeper)
        method = resolver.resolveSyncMethod("company/department/team/sub/project");
        assertThat(method).isEqualTo("push_mirror");

        // Should not match (missing level)
        method = resolver.resolveSyncMethod("company/department/project");
        assertThat(method).isEqualTo("push_mirror");
    }
}
