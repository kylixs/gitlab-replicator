package com.gitlab.mirror.server.mapper;

import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Push Mirror Config Mapper Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushMirrorConfigMapperTest {

    @Autowired
    private PushMirrorConfigMapper pushMirrorConfigMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-mirror-1");

        // Create push mirror config
        PushMirrorConfig config = new PushMirrorConfig();
        config.setSyncProjectId(syncProject.getId());
        config.setGitlabMirrorId(789L);
        config.setMirrorUrl("http://target.gitlab.com/group1/test-mirror-1.git");
        config.setLastUpdateStatus(PushMirrorConfig.UpdateStatus.FINISHED);
        config.setLastUpdateAt(LocalDateTime.now());
        config.setLastSuccessfulUpdateAt(LocalDateTime.now());
        config.setConsecutiveFailures(0);

        // Insert
        int result = pushMirrorConfigMapper.insert(config);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(config.getId()).isNotNull();
        assertThat(config.getCreatedAt()).isNotNull();
        assertThat(config.getUpdatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-mirror-2");
        PushMirrorConfig config = createPushMirrorConfig(syncProject.getId());

        // Query by ID
        PushMirrorConfig found = pushMirrorConfigMapper.selectById(config.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getGitlabMirrorId()).isEqualTo(789L);
        assertThat(found.getLastUpdateStatus()).isEqualTo(PushMirrorConfig.UpdateStatus.FINISHED);
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-mirror-3");
        PushMirrorConfig config = createPushMirrorConfig(syncProject.getId());

        // Update
        config.setLastUpdateStatus(PushMirrorConfig.UpdateStatus.FAILED);
        config.setConsecutiveFailures(3);
        config.setErrorMessage("Mirror update failed");
        int result = pushMirrorConfigMapper.updateById(config);

        // Verify
        assertThat(result).isEqualTo(1);
        PushMirrorConfig updated = pushMirrorConfigMapper.selectById(config.getId());
        assertThat(updated.getLastUpdateStatus()).isEqualTo(PushMirrorConfig.UpdateStatus.FAILED);
        assertThat(updated.getConsecutiveFailures()).isEqualTo(3);
        assertThat(updated.getErrorMessage()).isEqualTo("Mirror update failed");
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-mirror-4");
        PushMirrorConfig config = createPushMirrorConfig(syncProject.getId());

        // Delete
        int result = pushMirrorConfigMapper.deleteById(config.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        PushMirrorConfig deleted = pushMirrorConfigMapper.selectById(config.getId());
        assertThat(deleted).isNull();
    }

    private SyncProject createSyncProject(String projectKey) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);
        return project;
    }

    private PushMirrorConfig createPushMirrorConfig(Long syncProjectId) {
        PushMirrorConfig config = new PushMirrorConfig();
        config.setSyncProjectId(syncProjectId);
        config.setGitlabMirrorId(789L);
        config.setMirrorUrl("http://target.gitlab.com/group1/test-mirror.git");
        config.setLastUpdateStatus(PushMirrorConfig.UpdateStatus.FINISHED);
        config.setLastUpdateAt(LocalDateTime.now());
        config.setLastSuccessfulUpdateAt(LocalDateTime.now());
        config.setConsecutiveFailures(0);
        pushMirrorConfigMapper.insert(config);
        return config;
    }
}
