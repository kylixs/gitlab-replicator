package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pull Sync Config Mapper Test
 * <p>
 * MUST strictly perform unit testing for all CRUD operations
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PullSyncConfigMapperTest {

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-pull-1");

        // Create pull sync config
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(syncProject.getId());
        config.setPriority(PullSyncConfig.Priority.NORMAL);
        config.setEnabled(true);
        config.setLocalRepoPath("/data/repos/group1/test-pull-1");

        // Insert
        int result = pullSyncConfigMapper.insert(config);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(config.getId()).isNotNull();
        assertThat(config.getCreatedAt()).isNotNull();
        assertThat(config.getUpdatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-pull-2");
        PullSyncConfig config = createPullSyncConfig(syncProject.getId());

        // Query by ID
        PullSyncConfig found = pullSyncConfigMapper.selectById(config.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getPriority()).isEqualTo(PullSyncConfig.Priority.NORMAL);
        assertThat(found.getEnabled()).isTrue();
    }

    @Test
    void testSelectBySyncProjectId() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-pull-3");
        PullSyncConfig config = createPullSyncConfig(syncProject.getId());

        // Query by sync_project_id
        LambdaQueryWrapper<PullSyncConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PullSyncConfig::getSyncProjectId, syncProject.getId());
        PullSyncConfig found = pullSyncConfigMapper.selectOne(queryWrapper);

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(config.getId());
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-pull-4");
        PullSyncConfig config = createPullSyncConfig(syncProject.getId());

        // Update
        config.setPriority(PullSyncConfig.Priority.HIGH);
        config.setEnabled(false);
        int result = pullSyncConfigMapper.updateById(config);

        // Verify
        assertThat(result).isEqualTo(1);
        PullSyncConfig updated = pullSyncConfigMapper.selectById(config.getId());
        assertThat(updated.getPriority()).isEqualTo(PullSyncConfig.Priority.HIGH);
        assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-pull-5");
        PullSyncConfig config = createPullSyncConfig(syncProject.getId());

        // Delete
        int result = pullSyncConfigMapper.deleteById(config.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        PullSyncConfig deleted = pullSyncConfigMapper.selectById(config.getId());
        assertThat(deleted).isNull();
    }

    @Test
    void testUniqueSyncProjectId() {
        // Create sync project
        SyncProject syncProject = createSyncProject("group1/test-pull-unique");

        // Create first config
        PullSyncConfig config1 = new PullSyncConfig();
        config1.setSyncProjectId(syncProject.getId());
        config1.setPriority(PullSyncConfig.Priority.NORMAL);
        config1.setEnabled(true);
        pullSyncConfigMapper.insert(config1);

        // Try to create second config with same sync_project_id (should fail)
        PullSyncConfig config2 = new PullSyncConfig();
        config2.setSyncProjectId(syncProject.getId());
        config2.setPriority(PullSyncConfig.Priority.HIGH);
        config2.setEnabled(true);

        try {
            pullSyncConfigMapper.insert(config2);
            // If we reach here, the unique constraint is not working
            assertThat(false).as("Should throw exception for duplicate sync_project_id").isTrue();
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e.getMessage()).containsAnyOf("Duplicate", "duplicate", "unique", "UNIQUE");
        }
    }

    @Test
    void testCascadeDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-pull-cascade");
        PullSyncConfig config = createPullSyncConfig(syncProject.getId());

        // Delete sync project (should cascade delete config)
        syncProjectMapper.deleteById(syncProject.getId());

        // Verify config is also deleted
        PullSyncConfig found = pullSyncConfigMapper.selectById(config.getId());
        assertThat(found).isNull();
    }

    @Test
    void testDefaultValues() {
        // Create sync project
        SyncProject syncProject = createSyncProject("group1/test-pull-defaults");

        // Create config with minimal fields
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(syncProject.getId());
        pullSyncConfigMapper.insert(config);

        // Verify defaults
        PullSyncConfig found = pullSyncConfigMapper.selectById(config.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
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

    private PullSyncConfig createPullSyncConfig(Long syncProjectId) {
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(syncProjectId);
        config.setPriority(PullSyncConfig.Priority.NORMAL);
        config.setEnabled(true);
        config.setLocalRepoPath("/data/repos/test");
        pullSyncConfigMapper.insert(config);
        return config;
    }
}
