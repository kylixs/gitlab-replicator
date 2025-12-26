package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.BranchSnapshotService;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DiffCalculator Test
 *
 * @author GitLab Mirror Team
 */
class DiffCalculatorTest {

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Mock
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Mock
    private GitLabApiClient sourceGitLabApiClient;

    @Mock
    private GitLabApiClient targetGitLabApiClient;

    @Mock
    private BranchSnapshotService branchSnapshotService;

    private DiffCalculator diffCalculator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        diffCalculator = new DiffCalculator(syncProjectMapper, sourceProjectInfoMapper, targetProjectInfoMapper,
                sourceGitLabApiClient, targetGitLabApiClient, branchSnapshotService);
    }

    @Test
    void testCalculateDiff_synced() {
        // Mock sync project
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProject.setProjectKey("group/project1");

        // Mock source info - latest commit
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setLatestCommitSha("abc123");
        sourceInfo.setCommitCount(100);
        sourceInfo.setBranchCount(5);
        sourceInfo.setRepositorySize(1024L * 1024);
        sourceInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        sourceInfo.setDefaultBranch("main");

        // Mock target info - synced (same SHA, delay < 5 min)
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setLatestCommitSha("abc123");
        targetInfo.setCommitCount(100);
        targetInfo.setBranchCount(5);
        targetInfo.setRepositorySize(1024L * 1024);
        targetInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        targetInfo.setDefaultBranch("main");

        when(syncProjectMapper.selectById(1L)).thenReturn(syncProject);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        // Execute
        ProjectDiff result = diffCalculator.calculateDiff(1L);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getProjectKey()).isEqualTo("group/project1");
        assertThat(result.getStatus()).isEqualTo(ProjectDiff.SyncStatus.SYNCED);
        assertThat(result.getDiff().isCommitShaMatches()).isTrue();
        assertThat(result.getDiff().getCommitBehind()).isEqualTo(0);
    }

    @Test
    void testCalculateDiff_outdated() {
        // Mock sync project
        SyncProject syncProject = new SyncProject();
        syncProject.setId(2L);
        syncProject.setProjectKey("group/project2");

        // Mock source info - newer commit
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setLatestCommitSha("def456");
        sourceInfo.setCommitCount(120);
        sourceInfo.setBranchCount(5);
        sourceInfo.setRepositorySize(1024L * 1024);
        sourceInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(35));
        sourceInfo.setDefaultBranch("main");

        // Mock target info - old commit
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setLatestCommitSha("abc123");
        targetInfo.setCommitCount(100);
        targetInfo.setBranchCount(5);
        targetInfo.setRepositorySize(1024L * 1024);
        targetInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(35));
        targetInfo.setDefaultBranch("main");

        when(syncProjectMapper.selectById(2L)).thenReturn(syncProject);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        // Execute
        ProjectDiff result = diffCalculator.calculateDiff(2L);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ProjectDiff.SyncStatus.OUTDATED);
        assertThat(result.getDiff().isCommitShaMatches()).isFalse();
        assertThat(result.getDiff().getCommitBehind()).isEqualTo(20);
    }

    @Test
    void testCalculateDiff_failed_targetMissing() {
        // Mock sync project
        SyncProject syncProject = new SyncProject();
        syncProject.setId(3L);
        syncProject.setProjectKey("group/project3");

        // Mock source info
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setLatestCommitSha("abc123");
        sourceInfo.setCommitCount(100);
        sourceInfo.setBranchCount(5);
        sourceInfo.setRepositorySize(1024L * 1024);
        sourceInfo.setLastActivityAt(LocalDateTime.now());
        sourceInfo.setDefaultBranch("main");

        when(syncProjectMapper.selectById(3L)).thenReturn(syncProject);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(null);

        // Execute
        ProjectDiff result = diffCalculator.calculateDiff(3L);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ProjectDiff.SyncStatus.PENDING);
        assertThat(result.getTarget()).isNull();
    }

    @Test
    void testCalculateDiff_inconsistent_branchMismatch() {
        // Mock sync project
        SyncProject syncProject = new SyncProject();
        syncProject.setId(4L);
        syncProject.setProjectKey("group/project4");

        // Mock source info
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setLatestCommitSha("abc123");
        sourceInfo.setCommitCount(100);
        sourceInfo.setBranchCount(10);
        sourceInfo.setRepositorySize(1024L * 1024);
        sourceInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        sourceInfo.setDefaultBranch("main");

        // Mock target info - different branch count
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setLatestCommitSha("abc123");
        targetInfo.setCommitCount(100);
        targetInfo.setBranchCount(5); // Mismatch
        targetInfo.setRepositorySize(1024L * 1024);
        targetInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        targetInfo.setDefaultBranch("main");

        when(syncProjectMapper.selectById(4L)).thenReturn(syncProject);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        // Execute
        ProjectDiff result = diffCalculator.calculateDiff(4L);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ProjectDiff.SyncStatus.INCONSISTENT);
        assertThat(result.getDiff().getBranchDiff()).isEqualTo(-5);
    }

    @Test
    void testCalculateDiff_inconsistent_sizeMismatch() {
        // Mock sync project
        SyncProject syncProject = new SyncProject();
        syncProject.setId(5L);
        syncProject.setProjectKey("group/project5");

        // Mock source info
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setLatestCommitSha("abc123");
        sourceInfo.setCommitCount(100);
        sourceInfo.setBranchCount(5);
        sourceInfo.setRepositorySize(1024L * 1024 * 100); // 100MB
        sourceInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        sourceInfo.setDefaultBranch("main");

        // Mock target info - size diff > 10%
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setLatestCommitSha("abc123");
        targetInfo.setCommitCount(100);
        targetInfo.setBranchCount(5);
        targetInfo.setRepositorySize(1024L * 1024 * 112); // 112MB (12% diff)
        targetInfo.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        targetInfo.setDefaultBranch("main");

        when(syncProjectMapper.selectById(5L)).thenReturn(syncProject);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(sourceInfo);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);

        // Execute
        ProjectDiff result = diffCalculator.calculateDiff(5L);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ProjectDiff.SyncStatus.INCONSISTENT);
        assertThat(result.getDiff().getSizeDiffPercent()).isGreaterThan(10.0);
    }

    @Test
    void testCalculateDiffBatch_multipleProjects() {
        // Mock multiple projects
        SyncProject project1 = new SyncProject();
        project1.setId(1L);
        project1.setProjectKey("group/project1");

        SyncProject project2 = new SyncProject();
        project2.setId(2L);
        project2.setProjectKey("group/project2");

        SourceProjectInfo source1 = createMockSourceInfo("abc123");
        SourceProjectInfo source2 = createMockSourceInfo("def456");

        TargetProjectInfo target1 = createMockTargetInfo("abc123");
        TargetProjectInfo target2 = createMockTargetInfo("def456");

        when(syncProjectMapper.selectById(1L)).thenReturn(project1);
        when(syncProjectMapper.selectById(2L)).thenReturn(project2);
        when(sourceProjectInfoMapper.selectOne(any())).thenReturn(source1, source2);
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(target1, target2);

        // Execute
        List<ProjectDiff> results = diffCalculator.calculateDiffBatch(List.of(1L, 2L));

        // Verify
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.getStatus() == ProjectDiff.SyncStatus.SYNCED);
    }

    @Test
    void testCalculateDiff_nullHandling() {
        // Test null sync project
        when(syncProjectMapper.selectById(999L)).thenReturn(null);

        ProjectDiff result = diffCalculator.calculateDiff(999L);

        assertThat(result).isNull();
    }

    private SourceProjectInfo createMockSourceInfo(String commitSha) {
        SourceProjectInfo info = new SourceProjectInfo();
        info.setLatestCommitSha(commitSha);
        info.setCommitCount(100);
        info.setBranchCount(5);
        info.setRepositorySize(1024L * 1024);
        info.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        info.setDefaultBranch("main");
        return info;
    }

    private TargetProjectInfo createMockTargetInfo(String commitSha) {
        TargetProjectInfo info = new TargetProjectInfo();
        info.setLatestCommitSha(commitSha);
        info.setCommitCount(100);
        info.setBranchCount(5);
        info.setRepositorySize(1024L * 1024);
        info.setLastActivityAt(LocalDateTime.now().minusMinutes(2));
        info.setDefaultBranch("main");
        return info;
    }
}
