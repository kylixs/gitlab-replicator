package com.gitlab.mirror.server.scheduler;

import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.PushMirrorConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.PushMirrorManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MirrorCompensationScheduler
 */
@ExtendWith(MockitoExtension.class)
class MirrorCompensationSchedulerTest {

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Mock
    private PushMirrorConfigMapper pushMirrorConfigMapper;

    @Mock
    private PushMirrorManagementService pushMirrorManagementService;

    @Mock
    private GitLabMirrorProperties properties;

    @InjectMocks
    private MirrorCompensationScheduler scheduler;

    @BeforeEach
    void setUp() {
        GitLabMirrorProperties.SyncConfig syncConfig = new GitLabMirrorProperties.SyncConfig();
        syncConfig.setEnabled(true);
        when(properties.getSync()).thenReturn(syncConfig);
    }

    @Test
    void testOnApplicationReady_ShouldTriggerCompensation() {
        // Given
        when(syncProjectMapper.selectList(any())).thenReturn(Collections.emptyList());

        // When
        scheduler.onApplicationReady();

        // Then
        verify(syncProjectMapper, times(1)).selectList(any());
    }

    @Test
    void testCompensateMirrorConfiguration_WhenSyncDisabled_ShouldSkip() {
        // Given
        GitLabMirrorProperties.SyncConfig syncConfig = new GitLabMirrorProperties.SyncConfig();
        syncConfig.setEnabled(false);
        when(properties.getSync()).thenReturn(syncConfig);

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(syncProjectMapper, never()).selectList(any());
    }

    @Test
    void testCompensateMirrorConfiguration_WhenNoProjectsNeedCompensation_ShouldDoNothing() {
        // Given
        when(syncProjectMapper.selectList(any())).thenReturn(Collections.emptyList());

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(pushMirrorManagementService, never()).configureMirror(anyLong());
    }

    @Test
    void testCompensateMirrorConfiguration_WhenProjectNeedsMirror_ShouldCreateMirror() {
        // Given
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProject.setProjectKey("group1/project1");
        syncProject.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject.setEnabled(true);

        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setSyncProjectId(1L);
        targetInfo.setGitlabProjectId(100L);

        when(syncProjectMapper.selectList(any())).thenReturn(Arrays.asList(syncProject));
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);
        when(pushMirrorConfigMapper.selectBySyncProjectId(1L)).thenReturn(null);

        PushMirrorConfig createdConfig = new PushMirrorConfig();
        createdConfig.setGitlabMirrorId(200L);
        when(pushMirrorManagementService.configureMirror(1L)).thenReturn(createdConfig);

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(pushMirrorManagementService, times(1)).configureMirror(1L);
        verify(syncProjectMapper, times(1)).updateById(argThat(project ->
                SyncProject.SyncStatus.MIRROR_CONFIGURED.equals(project.getSyncStatus())
        ));
    }

    @Test
    void testCompensateMirrorConfiguration_WhenTargetProjectNotFound_ShouldSkip() {
        // Given
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProject.setProjectKey("group1/project1");
        syncProject.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject.setEnabled(true);

        when(syncProjectMapper.selectList(any())).thenReturn(Arrays.asList(syncProject));
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(null);

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(pushMirrorManagementService, never()).configureMirror(anyLong());
    }

    @Test
    void testCompensateMirrorConfiguration_WhenMirrorAlreadyConfigured_ShouldSkip() {
        // Given
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProject.setProjectKey("group1/project1");
        syncProject.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject.setEnabled(true);

        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setSyncProjectId(1L);

        PushMirrorConfig existingConfig = new PushMirrorConfig();
        existingConfig.setGitlabMirrorId(200L);

        when(syncProjectMapper.selectList(any())).thenReturn(Arrays.asList(syncProject));
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);
        when(pushMirrorConfigMapper.selectBySyncProjectId(1L)).thenReturn(existingConfig);

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(pushMirrorManagementService, never()).configureMirror(anyLong());
    }

    @Test
    void testCompensateMirrorConfiguration_WhenMirrorConfigExistsButNoMirrorId_ShouldCompensate() {
        // Given
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProject.setProjectKey("group1/project1");
        syncProject.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject.setEnabled(true);

        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setSyncProjectId(1L);

        PushMirrorConfig existingConfig = new PushMirrorConfig();
        existingConfig.setGitlabMirrorId(null);  // Mirror ID is null

        when(syncProjectMapper.selectList(any())).thenReturn(Arrays.asList(syncProject));
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);
        when(pushMirrorConfigMapper.selectBySyncProjectId(1L)).thenReturn(existingConfig);

        PushMirrorConfig createdConfig = new PushMirrorConfig();
        createdConfig.setGitlabMirrorId(200L);
        when(pushMirrorManagementService.configureMirror(1L)).thenReturn(createdConfig);

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(pushMirrorManagementService, times(1)).configureMirror(1L);
    }

    @Test
    void testCompensateMirrorConfiguration_WhenMirrorCreationFails_ShouldUpdateErrorMessage() {
        // Given
        SyncProject syncProject = new SyncProject();
        syncProject.setId(1L);
        syncProject.setProjectKey("group1/project1");
        syncProject.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject.setEnabled(true);

        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setSyncProjectId(1L);

        when(syncProjectMapper.selectList(any())).thenReturn(Arrays.asList(syncProject));
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo);
        when(pushMirrorConfigMapper.selectBySyncProjectId(1L)).thenReturn(null);
        when(pushMirrorManagementService.configureMirror(1L))
                .thenThrow(new RuntimeException("GitLab API error"));

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(syncProjectMapper, times(1)).updateById(argThat(project ->
                project.getErrorMessage() != null && project.getErrorMessage().contains("Mirror compensation failed")
        ));
    }

    @Test
    void testCompensateMirrorConfiguration_WithMultipleProjects_ShouldProcessAll() {
        // Given
        SyncProject syncProject1 = new SyncProject();
        syncProject1.setId(1L);
        syncProject1.setProjectKey("group1/project1");
        syncProject1.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject1.setEnabled(true);

        SyncProject syncProject2 = new SyncProject();
        syncProject2.setId(2L);
        syncProject2.setProjectKey("group1/project2");
        syncProject2.setSyncStatus(SyncProject.SyncStatus.TARGET_CREATED);
        syncProject2.setEnabled(true);

        TargetProjectInfo targetInfo1 = new TargetProjectInfo();
        targetInfo1.setSyncProjectId(1L);

        TargetProjectInfo targetInfo2 = new TargetProjectInfo();
        targetInfo2.setSyncProjectId(2L);

        when(syncProjectMapper.selectList(any())).thenReturn(Arrays.asList(syncProject1, syncProject2));
        when(targetProjectInfoMapper.selectOne(any())).thenReturn(targetInfo1, targetInfo2);
        when(pushMirrorConfigMapper.selectBySyncProjectId(anyLong())).thenReturn(null);

        PushMirrorConfig createdConfig = new PushMirrorConfig();
        createdConfig.setGitlabMirrorId(200L);
        when(pushMirrorManagementService.configureMirror(anyLong())).thenReturn(createdConfig);

        // When
        scheduler.compensateMirrorConfiguration();

        // Then
        verify(pushMirrorManagementService, times(2)).configureMirror(anyLong());
        verify(syncProjectMapper, times(2)).updateById(any(SyncProject.class));
    }
}
