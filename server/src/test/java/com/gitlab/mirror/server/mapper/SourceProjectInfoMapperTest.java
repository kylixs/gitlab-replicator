package com.gitlab.mirror.server.mapper;

import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source Project Info Mapper Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SourceProjectInfoMapperTest {

    @Autowired
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-source-1");

        // Create source project info
        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProject.getId());
        info.setGitlabProjectId(123L);
        info.setPathWithNamespace("group1/test-source-1");
        info.setGroupPath("group1");
        info.setName("test-source-1");
        info.setDefaultBranch("main");
        info.setVisibility("private");
        info.setArchived(false);
        info.setEmptyRepo(false);
        info.setStarCount(5);
        info.setForkCount(2);
        info.setLastActivityAt(LocalDateTime.now());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", "Test project");
        metadata.put("tags", new String[]{"test", "demo"});
        info.setMetadata(metadata);

        info.setSyncedAt(LocalDateTime.now());

        // Insert
        int result = sourceProjectInfoMapper.insert(info);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(info.getId()).isNotNull();
        assertThat(info.getUpdatedAt()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-source-2");
        SourceProjectInfo info = createSourceProjectInfo(syncProject.getId());

        // Query by ID
        SourceProjectInfo found = sourceProjectInfoMapper.selectById(info.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getGitlabProjectId()).isEqualTo(123L);
        assertThat(found.getPathWithNamespace()).isEqualTo("group1/test-source");
        assertThat(found.getMetadata()).isNotNull();
        assertThat(found.getMetadata()).containsKey("description");
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-source-3");
        SourceProjectInfo info = createSourceProjectInfo(syncProject.getId());

        // Update
        info.setStarCount(10);
        info.setArchived(true);
        int result = sourceProjectInfoMapper.updateById(info);

        // Verify
        assertThat(result).isEqualTo(1);
        SourceProjectInfo updated = sourceProjectInfoMapper.selectById(info.getId());
        assertThat(updated.getStarCount()).isEqualTo(10);
        assertThat(updated.getArchived()).isTrue();
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-source-4");
        SourceProjectInfo info = createSourceProjectInfo(syncProject.getId());

        // Delete
        int result = sourceProjectInfoMapper.deleteById(info.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        SourceProjectInfo deleted = sourceProjectInfoMapper.selectById(info.getId());
        assertThat(deleted).isNull();
    }

    @Test
    void testMonitoringFieldsInsertAndRead() {
        // Test new monitoring fields: latest_commit_sha, commit_count, branch_count
        SyncProject syncProject = createSyncProject("group1/test-monitor-1");

        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProject.getId());
        info.setGitlabProjectId(456L);
        info.setPathWithNamespace("group1/test-monitor-1");
        info.setGroupPath("group1");
        info.setName("test-monitor-1");
        info.setDefaultBranch("main");

        // Set new monitoring fields
        info.setLatestCommitSha("abc123def456");
        info.setCommitCount(245);
        info.setBranchCount(3);
        info.setRepositorySize(15925248L);
        info.setLastActivityAt(LocalDateTime.now());

        // Insert
        int result = sourceProjectInfoMapper.insert(info);

        // Verify insert
        assertThat(result).isEqualTo(1);
        assertThat(info.getId()).isNotNull();

        // Query and verify monitoring fields
        SourceProjectInfo found = sourceProjectInfoMapper.selectById(info.getId());
        assertThat(found).isNotNull();
        assertThat(found.getLatestCommitSha()).isEqualTo("abc123def456");
        assertThat(found.getCommitCount()).isEqualTo(245);
        assertThat(found.getBranchCount()).isEqualTo(3);
        assertThat(found.getRepositorySize()).isEqualTo(15925248L);
        assertThat(found.getLastActivityAt()).isNotNull();
    }

    @Test
    void testMonitoringFieldsUpdate() {
        // Test updating monitoring fields
        SyncProject syncProject = createSyncProject("group1/test-monitor-2");
        SourceProjectInfo info = createSourceProjectInfo(syncProject.getId());

        // Update monitoring fields
        info.setLatestCommitSha("new-sha-789");
        info.setCommitCount(300);
        info.setBranchCount(5);
        info.setRepositorySize(20000000L);
        info.setLastActivityAt(LocalDateTime.now().plusHours(1));

        int result = sourceProjectInfoMapper.updateById(info);

        // Verify update
        assertThat(result).isEqualTo(1);
        SourceProjectInfo updated = sourceProjectInfoMapper.selectById(info.getId());
        assertThat(updated.getLatestCommitSha()).isEqualTo("new-sha-789");
        assertThat(updated.getCommitCount()).isEqualTo(300);
        assertThat(updated.getBranchCount()).isEqualTo(5);
        assertThat(updated.getRepositorySize()).isEqualTo(20000000L);
    }

    @Test
    void testMonitoringFieldsNullValues() {
        // Test that monitoring fields can be null
        SyncProject syncProject = createSyncProject("group1/test-monitor-3");

        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProject.getId());
        info.setGitlabProjectId(789L);
        info.setPathWithNamespace("group1/test-monitor-3");
        info.setGroupPath("group1");
        info.setName("test-monitor-3");
        info.setDefaultBranch("main");

        // Don't set monitoring fields (should be null)

        // Insert
        int result = sourceProjectInfoMapper.insert(info);

        // Verify
        assertThat(result).isEqualTo(1);
        SourceProjectInfo found = sourceProjectInfoMapper.selectById(info.getId());
        assertThat(found.getLatestCommitSha()).isNull();
        assertThat(found.getCommitCount()).isNull();
        assertThat(found.getBranchCount()).isNull();
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

    private SourceProjectInfo createSourceProjectInfo(Long syncProjectId) {
        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(123L);
        info.setPathWithNamespace("group1/test-source");
        info.setGroupPath("group1");
        info.setName("test-source");
        info.setDefaultBranch("main");
        info.setVisibility("private");
        info.setArchived(false);
        info.setEmptyRepo(false);
        info.setStarCount(5);
        info.setForkCount(2);
        info.setLastActivityAt(LocalDateTime.now());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", "Test project");
        info.setMetadata(metadata);

        info.setSyncedAt(LocalDateTime.now());
        sourceProjectInfoMapper.insert(info);
        return info;
    }
}
