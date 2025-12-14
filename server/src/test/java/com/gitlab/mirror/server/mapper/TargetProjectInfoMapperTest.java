package com.gitlab.mirror.server.mapper;

import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Target Project Info Mapper Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TargetProjectInfoMapperTest {

    @Autowired
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-target-1");

        // Create target project info
        TargetProjectInfo info = new TargetProjectInfo();
        info.setSyncProjectId(syncProject.getId());
        info.setGitlabProjectId(456L);
        info.setPathWithNamespace("group1/test-target-1");
        info.setName("test-target-1");
        info.setVisibility("private");
        info.setStatus(TargetProjectInfo.Status.CREATED);
        info.setLastCheckedAt(LocalDateTime.now());
        info.setRetryCount(0);

        // Insert
        int result = targetProjectInfoMapper.insert(info);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(info.getId()).isNotNull();
        assertThat(info.getCreatedAt()).isNotNull();
        assertThat(info.getUpdatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-target-2");
        TargetProjectInfo info = createTargetProjectInfo(syncProject.getId());

        // Query by ID
        TargetProjectInfo found = targetProjectInfoMapper.selectById(info.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getGitlabProjectId()).isEqualTo(456L);
        assertThat(found.getStatus()).isEqualTo(TargetProjectInfo.Status.CREATED);
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-target-3");
        TargetProjectInfo info = createTargetProjectInfo(syncProject.getId());

        // Update
        info.setStatus(TargetProjectInfo.Status.READY);
        info.setRetryCount(1);
        int result = targetProjectInfoMapper.updateById(info);

        // Verify
        assertThat(result).isEqualTo(1);
        TargetProjectInfo updated = targetProjectInfoMapper.selectById(info.getId());
        assertThat(updated.getStatus()).isEqualTo(TargetProjectInfo.Status.READY);
        assertThat(updated.getRetryCount()).isEqualTo(1);
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-target-4");
        TargetProjectInfo info = createTargetProjectInfo(syncProject.getId());

        // Delete
        int result = targetProjectInfoMapper.deleteById(info.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        TargetProjectInfo deleted = targetProjectInfoMapper.selectById(info.getId());
        assertThat(deleted).isNull();
    }

    @Test
    void testMonitoringFieldsInsertAndRead() {
        // Test new monitoring fields
        SyncProject syncProject = createSyncProject("group1/test-target-monitor-1");

        TargetProjectInfo info = new TargetProjectInfo();
        info.setSyncProjectId(syncProject.getId());
        info.setGitlabProjectId(789L);
        info.setPathWithNamespace("group1/test-target-monitor-1");
        info.setName("test-target-monitor-1");
        info.setStatus(TargetProjectInfo.Status.READY);

        // Set new monitoring fields
        info.setDefaultBranch("main");
        info.setLatestCommitSha("xyz789abc123");
        info.setCommitCount(240);
        info.setBranchCount(3);
        info.setRepositorySize(15823456L);
        info.setLastActivityAt(LocalDateTime.now());

        // Insert
        int result = targetProjectInfoMapper.insert(info);

        // Verify insert
        assertThat(result).isEqualTo(1);
        assertThat(info.getId()).isNotNull();

        // Query and verify monitoring fields
        TargetProjectInfo found = targetProjectInfoMapper.selectById(info.getId());
        assertThat(found).isNotNull();
        assertThat(found.getDefaultBranch()).isEqualTo("main");
        assertThat(found.getLatestCommitSha()).isEqualTo("xyz789abc123");
        assertThat(found.getCommitCount()).isEqualTo(240);
        assertThat(found.getBranchCount()).isEqualTo(3);
        assertThat(found.getRepositorySize()).isEqualTo(15823456L);
        assertThat(found.getLastActivityAt()).isNotNull();
    }

    @Test
    void testMonitoringFieldsUpdate() {
        // Test updating monitoring fields
        SyncProject syncProject = createSyncProject("group1/test-target-monitor-2");
        TargetProjectInfo info = createTargetProjectInfo(syncProject.getId());

        // Update monitoring fields
        info.setDefaultBranch("develop");
        info.setLatestCommitSha("updated-sha-456");
        info.setCommitCount(250);
        info.setBranchCount(4);
        info.setRepositorySize(18000000L);
        info.setLastActivityAt(LocalDateTime.now().plusDays(1));

        int result = targetProjectInfoMapper.updateById(info);

        // Verify update
        assertThat(result).isEqualTo(1);
        TargetProjectInfo updated = targetProjectInfoMapper.selectById(info.getId());
        assertThat(updated.getDefaultBranch()).isEqualTo("develop");
        assertThat(updated.getLatestCommitSha()).isEqualTo("updated-sha-456");
        assertThat(updated.getCommitCount()).isEqualTo(250);
        assertThat(updated.getBranchCount()).isEqualTo(4);
        assertThat(updated.getRepositorySize()).isEqualTo(18000000L);
    }

    @Test
    void testMonitoringFieldsNullValues() {
        // Test that monitoring fields can be null
        SyncProject syncProject = createSyncProject("group1/test-target-monitor-3");

        TargetProjectInfo info = new TargetProjectInfo();
        info.setSyncProjectId(syncProject.getId());
        info.setGitlabProjectId(999L);
        info.setPathWithNamespace("group1/test-target-monitor-3");
        info.setName("test-target-monitor-3");
        info.setStatus(TargetProjectInfo.Status.NOT_EXIST);

        // Don't set monitoring fields (should be null)

        // Insert
        int result = targetProjectInfoMapper.insert(info);

        // Verify
        assertThat(result).isEqualTo(1);
        TargetProjectInfo found = targetProjectInfoMapper.selectById(info.getId());
        assertThat(found.getDefaultBranch()).isNull();
        assertThat(found.getLatestCommitSha()).isNull();
        assertThat(found.getCommitCount()).isNull();
        assertThat(found.getBranchCount()).isNull();
        assertThat(found.getRepositorySize()).isNull();
        assertThat(found.getLastActivityAt()).isNull();
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

    private TargetProjectInfo createTargetProjectInfo(Long syncProjectId) {
        TargetProjectInfo info = new TargetProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(456L);
        info.setPathWithNamespace("group1/test-target");
        info.setName("test-target");
        info.setVisibility("private");
        info.setStatus(TargetProjectInfo.Status.CREATED);
        info.setLastCheckedAt(LocalDateTime.now());
        info.setRetryCount(0);
        targetProjectInfoMapper.insert(info);
        return info;
    }
}
