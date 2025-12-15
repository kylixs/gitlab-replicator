package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Update Project Data Service Test
 *
 * @author GitLab Mirror Team
 */
class UpdateProjectDataServiceTest {

    @Mock
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Mock
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Mock
    private BatchQueryExecutor batchQueryExecutor;

    private UpdateProjectDataService updateProjectDataService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        updateProjectDataService = new UpdateProjectDataService(sourceProjectInfoMapper, targetProjectInfoMapper, batchQueryExecutor);
    }

    @Test
    void testUpdateSourceProjects_success() {
        // Prepare test data
        GitLabProject project1 = createMockProject(1L, "group1/project1");
        GitLabProject project2 = createMockProject(2L, "group1/project2");
        List<GitLabProject> projects = List.of(project1, project2);

        BatchQueryExecutor.ProjectDetails details1 = createMockDetails(1L, "sha1", 100, 5);
        BatchQueryExecutor.ProjectDetails details2 = createMockDetails(2L, "sha2", 200, 3);
        Map<Long, BatchQueryExecutor.ProjectDetails> detailsMap = new HashMap<>();
        detailsMap.put(1L, details1);
        detailsMap.put(2L, details2);

        SourceProjectInfo info1 = new SourceProjectInfo();
        info1.setId(10L);
        info1.setGitlabProjectId(1L);

        SourceProjectInfo info2 = new SourceProjectInfo();
        info2.setId(20L);
        info2.setGitlabProjectId(2L);

        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(info1, info2);
        when(sourceProjectInfoMapper.updateById(any())).thenReturn(1);

        // Execute
        UpdateProjectDataService.UpdateResult result = updateProjectDataService.updateSourceProjects(projects, detailsMap);

        // Verify
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(0);
        verify(sourceProjectInfoMapper, times(2)).updateById(any());
    }

    @Test
    void testUpdateSourceProjects_projectNotFound() {
        // Prepare test data
        GitLabProject project = createMockProject(999L, "group1/missing");
        List<GitLabProject> projects = List.of(project);
        Map<Long, BatchQueryExecutor.ProjectDetails> detailsMap = new HashMap<>();

        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(null);

        // Execute
        UpdateProjectDataService.UpdateResult result = updateProjectDataService.updateSourceProjects(projects, detailsMap);

        // Verify
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(sourceProjectInfoMapper, never()).updateById(any());
    }

    @Test
    void testUpdateTargetProjects_success() {
        // Prepare test data
        GitLabProject project = createMockProject(1L, "group1/project1");
        List<GitLabProject> projects = List.of(project);

        BatchQueryExecutor.ProjectDetails details = createMockDetails(1L, "target-sha", 95, 5);
        Map<Long, BatchQueryExecutor.ProjectDetails> detailsMap = Map.of(1L, details);

        TargetProjectInfo info = new TargetProjectInfo();
        info.setId(10L);
        info.setGitlabProjectId(1L);

        when(targetProjectInfoMapper.selectOne(any())).thenReturn(info);
        when(targetProjectInfoMapper.updateById(any())).thenReturn(1);

        // Execute
        UpdateProjectDataService.UpdateResult result = updateProjectDataService.updateTargetProjects(projects, detailsMap);

        // Verify
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(targetProjectInfoMapper).updateById(argThat(targetInfo -> {
            TargetProjectInfo updated = (TargetProjectInfo) targetInfo;
            return "target-sha".equals(updated.getLatestCommitSha())
                    && updated.getBranchCount() == 5
                    && updated.getCommitCount() == 95;
        }));
    }

    @Test
    void testUpdateSourceProjects_updateFailed() {
        // Prepare test data
        GitLabProject project = createMockProject(1L, "group1/project1");
        List<GitLabProject> projects = List.of(project);
        Map<Long, BatchQueryExecutor.ProjectDetails> detailsMap = new HashMap<>();

        SourceProjectInfo info = new SourceProjectInfo();
        info.setId(10L);
        info.setGitlabProjectId(1L);

        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(info);
        when(sourceProjectInfoMapper.updateById(any())).thenReturn(0); // Update failed

        // Execute
        UpdateProjectDataService.UpdateResult result = updateProjectDataService.updateSourceProjects(projects, detailsMap);

        // Verify
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.hasErrors()).isTrue();
    }

    private GitLabProject createMockProject(Long id, String pathWithNamespace) {
        GitLabProject project = new GitLabProject();
        project.setId(id);
        project.setPathWithNamespace(pathWithNamespace);
        project.setDefaultBranch("main");
        project.setLastActivityAt(OffsetDateTime.now());

        GitLabProject.Statistics stats = new GitLabProject.Statistics();
        stats.setRepositorySize(1024L * 1024);
        project.setStatistics(stats);

        return project;
    }

    private BatchQueryExecutor.ProjectDetails createMockDetails(Long projectId, String sha, Integer commitCount, Integer branchCount) {
        BatchQueryExecutor.ProjectDetails details = new BatchQueryExecutor.ProjectDetails();
        details.setProjectId(projectId);
        details.setLatestCommitSha(sha);
        details.setCommitCount(commitCount);
        details.setBranchCount(branchCount);
        return details;
    }
}
