package com.gitlab.mirror.server.mapper;

import com.gitlab.mirror.server.entity.SyncProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sync Project Mapper Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SyncProjectMapperTest {

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create entity
        SyncProject project = new SyncProject();
        project.setProjectKey("group1/test-project");
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);

        // Insert
        int result = syncProjectMapper.insert(project);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(project.getId()).isNotNull();
        assertThat(project.getCreatedAt()).isNotNull();
        assertThat(project.getUpdatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Insert first
        SyncProject project = new SyncProject();
        project.setProjectKey("group1/test-project-2");
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);

        // Query by ID
        SyncProject found = syncProjectMapper.selectById(project.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getProjectKey()).isEqualTo("group1/test-project-2");
        assertThat(found.getSyncMethod()).isEqualTo(SyncProject.SyncMethod.PUSH_MIRROR);
    }

    @Test
    void testUpdate() {
        // Insert first
        SyncProject project = new SyncProject();
        project.setProjectKey("group1/test-project-3");
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);

        // Update
        project.setSyncStatus(SyncProject.SyncStatus.ACTIVE);
        int result = syncProjectMapper.updateById(project);

        // Verify
        assertThat(result).isEqualTo(1);
        SyncProject updated = syncProjectMapper.selectById(project.getId());
        assertThat(updated.getSyncStatus()).isEqualTo(SyncProject.SyncStatus.ACTIVE);
    }
}
