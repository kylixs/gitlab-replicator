package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.client.RetryableGitLabClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Batch Query Executor Test
 *
 * @author GitLab Mirror Team
 */
class BatchQueryExecutorTest {

    @Mock
    private RetryableGitLabClient sourceClient;

    @Mock
    private RetryableGitLabClient targetClient;

    private BatchQueryExecutor batchQueryExecutor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        batchQueryExecutor = new BatchQueryExecutor(sourceClient, targetClient);
    }

    @Test
    void testQuerySourceProjects_withoutIncrementalFilter() {
        // Mock API response - first page has less than pageSize, so pagination stops
        GitLabProject[] page1 = createMockProjects(30, 1);

        when(sourceClient.get(anyString(), eq(GitLabProject[].class))).thenReturn(page1);

        // Execute
        List<GitLabProject> result = batchQueryExecutor.querySourceProjects(null, 50);

        // Verify
        assertThat(result).hasSize(30);
        verify(sourceClient, atLeastOnce()).get(anyString(), eq(GitLabProject[].class));
    }

    @Test
    void testQuerySourceProjects_withIncrementalFilter() {
        // Mock API response
        LocalDateTime updatedAfter = LocalDateTime.now().minusHours(1);
        GitLabProject[] projects = createMockProjects(10, 1);

        when(sourceClient.get(contains("updated_after"), eq(GitLabProject[].class))).thenReturn(projects);
        when(sourceClient.get(contains("page=2"), eq(GitLabProject[].class))).thenReturn(new GitLabProject[0]);

        // Execute
        List<GitLabProject> result = batchQueryExecutor.querySourceProjects(updatedAfter, 50);

        // Verify
        assertThat(result).hasSize(10);
        verify(sourceClient).get(contains("updated_after"), eq(GitLabProject[].class));
    }

    @Test
    void testQuerySourceProjects_withStatisticsParameter() {
        // Mock API response
        GitLabProject[] projects = createMockProjects(5, 1);
        when(sourceClient.get(anyString(), eq(GitLabProject[].class))).thenReturn(projects);

        // Execute
        batchQueryExecutor.querySourceProjects(null, 50);

        // Verify statistics parameter is included
        verify(sourceClient).get(contains("statistics=true"), eq(GitLabProject[].class));
    }

    @Test
    void testGetProjectDetails_success() {
        // Mock project
        GitLabProject project = new GitLabProject();
        project.setId(123L);
        project.setDefaultBranch("main");

        // Mock branches
        RepositoryBranch[] branches = new RepositoryBranch[3];
        for (int i = 0; i < 3; i++) {
            branches[i] = new RepositoryBranch();
        }

        // Mock default branch
        RepositoryBranch defaultBranch = new RepositoryBranch();
        RepositoryBranch.Commit commit = new RepositoryBranch.Commit();
        commit.setId("abc123def456");
        defaultBranch.setCommit(commit);

        when(sourceClient.get(contains("/projects/123"), eq(GitLabProject.class))).thenReturn(project);
        when(sourceClient.get(contains("/branches"), eq(RepositoryBranch[].class))).thenReturn(branches);
        when(sourceClient.get(contains("/branches/main"), eq(RepositoryBranch.class))).thenReturn(defaultBranch);

        // Execute
        BatchQueryExecutor.ProjectDetails details = batchQueryExecutor.getProjectDetails(123L, sourceClient);

        // Verify
        assertThat(details).isNotNull();
        assertThat(details.getProjectId()).isEqualTo(123L);
        assertThat(details.getBranchCount()).isEqualTo(3);
        assertThat(details.getLatestCommitSha()).isEqualTo("abc123def456");
    }

    @Test
    void testGetProjectDetailsBatch_concurrentExecution() {
        // Mock projects
        List<Long> projectIds = List.of(1L, 2L, 3L, 4L, 5L);

        for (Long id : projectIds) {
            GitLabProject project = new GitLabProject();
            project.setId(id);
            project.setDefaultBranch("main");

            RepositoryBranch[] branches = new RepositoryBranch[2];
            branches[0] = new RepositoryBranch();
            branches[1] = new RepositoryBranch();

            RepositoryBranch defaultBranch = new RepositoryBranch();
            RepositoryBranch.Commit commit = new RepositoryBranch.Commit();
            commit.setId("sha" + id);
            defaultBranch.setCommit(commit);

            when(sourceClient.get(contains("/projects/" + id), eq(GitLabProject.class))).thenReturn(project);
            when(sourceClient.get(contains("/" + id + "/repository/branches"), eq(RepositoryBranch[].class))).thenReturn(branches);
            when(sourceClient.get(contains("/" + id + "/repository/branches/main"), eq(RepositoryBranch.class))).thenReturn(defaultBranch);
        }

        // Execute
        List<BatchQueryExecutor.ProjectDetails> results = batchQueryExecutor.getProjectDetailsBatch(projectIds, sourceClient);

        // Verify
        assertThat(results).hasSize(5);
        assertThat(results).allMatch(d -> d.getBranchCount() == 2);
        assertThat(results).extracting(BatchQueryExecutor.ProjectDetails::getLatestCommitSha)
                .containsExactlyInAnyOrder("sha1", "sha2", "sha3", "sha4", "sha5");
    }

    private GitLabProject[] createMockProjects(int count, int startId) {
        GitLabProject[] projects = new GitLabProject[count];
        for (int i = 0; i < count; i++) {
            GitLabProject project = new GitLabProject();
            project.setId((long) (startId + i));
            project.setName("Project " + (startId + i));
            project.setPathWithNamespace("group/project-" + (startId + i));
            project.setDefaultBranch("main");
            project.setLastActivityAt(OffsetDateTime.now());

            // Add statistics
            GitLabProject.Statistics stats = new GitLabProject.Statistics();
            stats.setRepositorySize(1024L * 1024 * (startId + i));
            project.setStatistics(stats);

            projects[i] = project;
        }
        return projects;
    }
}
