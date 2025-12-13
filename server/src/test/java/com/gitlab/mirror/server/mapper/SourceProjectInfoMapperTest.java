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
