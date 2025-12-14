package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.server.entity.MonitorAlert;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.MonitorAlertMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.service.monitor.model.DiffDetails;
import com.gitlab.mirror.server.service.monitor.model.DiscoveryResult;
import com.gitlab.mirror.server.service.monitor.model.ProjectDiff;
import com.gitlab.mirror.server.service.monitor.model.ProjectSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProjectDiscoveryService Test
 *
 * @author GitLab Mirror Team
 */
class ProjectDiscoveryServiceTest {

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private MonitorAlertMapper monitorAlertMapper;

    private ProjectDiscoveryService projectDiscoveryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        projectDiscoveryService = new ProjectDiscoveryService(syncProjectMapper, monitorAlertMapper);
    }

    @Test
    void testDetectNewProjects_targetMissing() {
        // Create diff with target missing
        List<ProjectDiff> diffs = new ArrayList<>();
        ProjectDiff diff = ProjectDiff.builder()
                .projectKey("new/project")
                .status(ProjectDiff.SyncStatus.FAILED)
                .source(ProjectSnapshot.builder().build())
                .target(null)
                .diff(DiffDetails.builder().build())
                .build();
        diffs.add(diff);

        when(syncProjectMapper.selectOne(any())).thenReturn(null);
        when(syncProjectMapper.insert(any())).thenReturn(1);

        // Execute
        int result = projectDiscoveryService.detectNewProjects(diffs);

        // Verify
        assertThat(result).isEqualTo(1);
        verify(syncProjectMapper).insert(any(SyncProject.class));
    }

    @Test
    void testDetectNewProjects_alreadyExists() {
        // Create diff with target missing
        List<ProjectDiff> diffs = new ArrayList<>();
        ProjectDiff diff = ProjectDiff.builder()
                .projectKey("existing/project")
                .status(ProjectDiff.SyncStatus.FAILED)
                .source(ProjectSnapshot.builder().build())
                .target(null)
                .diff(DiffDetails.builder().build())
                .build();
        diffs.add(diff);

        SyncProject existing = new SyncProject();
        existing.setProjectKey("existing/project");
        when(syncProjectMapper.selectOne(any())).thenReturn(existing);

        // Execute
        int result = projectDiscoveryService.detectNewProjects(diffs);

        // Verify
        assertThat(result).isEqualTo(1);
        verify(syncProjectMapper, never()).insert(any());
    }

    @Test
    void testDetectUpdatedProjects() {
        // Create diff with outdated status
        List<ProjectDiff> diffs = new ArrayList<>();
        ProjectDiff diff = ProjectDiff.builder()
                .projectKey("updated/project")
                .status(ProjectDiff.SyncStatus.OUTDATED)
                .source(ProjectSnapshot.builder().commitSha("abc123").build())
                .target(ProjectSnapshot.builder().commitSha("def456").build())
                .diff(DiffDetails.builder()
                        .commitShaMatches(false)
                        .commitBehind(10)
                        .build())
                .build();
        diffs.add(diff);

        // Execute
        int result = projectDiscoveryService.detectUpdatedProjects(diffs);

        // Verify
        assertThat(result).isEqualTo(1);
    }

    @Test
    void testDetectDeletedProjects() {
        // Mock existing sync projects
        List<SyncProject> syncProjects = new ArrayList<>();
        SyncProject project1 = new SyncProject();
        project1.setId(1L);
        project1.setProjectKey("project1");
        project1.setSyncStatus("active");
        syncProjects.add(project1);

        SyncProject project2 = new SyncProject();
        project2.setId(2L);
        project2.setProjectKey("project2");
        project2.setSyncStatus("active");
        syncProjects.add(project2);

        when(syncProjectMapper.selectList(any())).thenReturn(syncProjects);
        when(syncProjectMapper.updateById(any())).thenReturn(1);
        when(monitorAlertMapper.insert(any())).thenReturn(1);

        // Only project1 exists in GitLab
        Set<String> existingKeys = new HashSet<>();
        existingKeys.add("project1");

        // Execute
        int result = projectDiscoveryService.detectDeletedProjects(existingKeys);

        // Verify
        assertThat(result).isEqualTo(1);
        verify(syncProjectMapper).updateById(any(SyncProject.class));
        verify(monitorAlertMapper).insert(any(MonitorAlert.class));
    }

    @Test
    void testPerformDiscovery() {
        // Mock diffs
        List<ProjectDiff> diffs = new ArrayList<>();
        ProjectDiff newProject = ProjectDiff.builder()
                .projectKey("new/project")
                .status(ProjectDiff.SyncStatus.FAILED)
                .source(ProjectSnapshot.builder().build())
                .target(null)
                .diff(DiffDetails.builder().build())
                .build();
        ProjectDiff outdatedProject = ProjectDiff.builder()
                .projectKey("outdated/project")
                .status(ProjectDiff.SyncStatus.OUTDATED)
                .source(ProjectSnapshot.builder().commitSha("abc").build())
                .target(ProjectSnapshot.builder().commitSha("def").build())
                .diff(DiffDetails.builder().commitShaMatches(false).build())
                .build();
        diffs.add(newProject);
        diffs.add(outdatedProject);

        when(syncProjectMapper.selectOne(any())).thenReturn(null);
        when(syncProjectMapper.insert(any())).thenReturn(1);
        when(syncProjectMapper.selectList(any())).thenReturn(new ArrayList<>());

        Set<String> existingKeys = new HashSet<>();
        existingKeys.add("new/project");
        existingKeys.add("outdated/project");

        // Execute
        DiscoveryResult result = projectDiscoveryService.performDiscovery(diffs, existingKeys);

        // Verify
        assertThat(result).isNotNull();
        assertThat(result.getNewProjects()).isEqualTo(1);
        assertThat(result.getUpdatedProjects()).isEqualTo(1);
        assertThat(result.getProjectsNeedingSync()).isEqualTo(1);
    }
}
