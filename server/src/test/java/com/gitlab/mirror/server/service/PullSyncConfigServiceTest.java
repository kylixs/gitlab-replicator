package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pull Sync Config Service Test
 * <p>
 * MUST strictly perform unit testing
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PullSyncConfigServiceTest {

    @Autowired
    private PullSyncConfigService pullSyncConfigService;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Test
    void testInitializeConfig() {
        // Create sync project
        SyncProject project = createSyncProject("group1/test-init");

        // Initialize config
        PullSyncConfig config = pullSyncConfigService.initializeConfig(project.getId(), project.getProjectKey());

        // Verify
        assertThat(config).isNotNull();
        assertThat(config.getId()).isNotNull();
        assertThat(config.getSyncProjectId()).isEqualTo(project.getId());
        assertThat(config.getPriority()).isEqualTo(PullSyncConfig.Priority.NORMAL);
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getLocalRepoPath()).contains("group1/test-init");
    }

    @Test
    void testInitializeConfigIdempotent() {
        // Create sync project
        SyncProject project = createSyncProject("group1/test-idempotent");

        // Initialize first time
        PullSyncConfig config1 = pullSyncConfigService.initializeConfig(project.getId(), project.getProjectKey());

        // Initialize second time (should return existing)
        PullSyncConfig config2 = pullSyncConfigService.initializeConfig(project.getId(), project.getProjectKey());

        // Verify same config returned
        assertThat(config1.getId()).isEqualTo(config2.getId());
    }

    @Test
    void testUpdatePriority() {
        // Create and initialize
        SyncProject project = createSyncProject("group1/test-priority");
        pullSyncConfigService.initializeConfig(project.getId(), project.getProjectKey());

        // Update priority
        pullSyncConfigService.updatePriority(project.getId(), PullSyncConfig.Priority.HIGH);

        // Verify
        PullSyncConfig config = pullSyncConfigService.getConfig(project.getId());
        assertThat(config.getPriority()).isEqualTo(PullSyncConfig.Priority.HIGH);
    }

    @Test
    void testUpdatePriorityNotFound() {
        // Try to update non-existent config
        assertThatThrownBy(() ->
                pullSyncConfigService.updatePriority(999999L, PullSyncConfig.Priority.HIGH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pull sync config not found");
    }

    @Test
    void testSetEnabled() {
        // Create and initialize
        SyncProject project = createSyncProject("group1/test-enabled");
        pullSyncConfigService.initializeConfig(project.getId(), project.getProjectKey());

        // Disable
        pullSyncConfigService.setEnabled(project.getId(), false);

        // Verify
        PullSyncConfig config = pullSyncConfigService.getConfig(project.getId());
        assertThat(config.getEnabled()).isFalse();

        // Enable again
        pullSyncConfigService.setEnabled(project.getId(), true);

        // Verify
        config = pullSyncConfigService.getConfig(project.getId());
        assertThat(config.getEnabled()).isTrue();
    }

    @Test
    void testSetEnabledNotFound() {
        // Try to update non-existent config
        assertThatThrownBy(() ->
                pullSyncConfigService.setEnabled(999999L, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pull sync config not found");
    }

    @Test
    void testGetConfig() {
        // Create and initialize
        SyncProject project = createSyncProject("group1/test-get");
        PullSyncConfig created = pullSyncConfigService.initializeConfig(project.getId(), project.getProjectKey());

        // Get config
        PullSyncConfig config = pullSyncConfigService.getConfig(project.getId());

        // Verify
        assertThat(config).isNotNull();
        assertThat(config.getId()).isEqualTo(created.getId());
        assertThat(config.getSyncProjectId()).isEqualTo(project.getId());
    }

    @Test
    void testGetConfigNotFound() {
        // Get non-existent config
        PullSyncConfig config = pullSyncConfigService.getConfig(999999L);

        // Verify
        assertThat(config).isNull();
    }

    private SyncProject createSyncProject(String projectKey) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod("pull_sync");
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);
        return project;
    }
}
