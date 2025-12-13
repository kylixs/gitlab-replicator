package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.GitLabGroup;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.client.GitLabClientException;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TargetProjectManagementService
 *
 * 测试要求：
 * - 测试创建单个项目
 * - 测试创建嵌套分组项目
 * - 测试处理已存在项目
 * - 测试重试机制
 * - 测试状态检查
 * - 测试批量创建
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class TargetProjectManagementServiceTest {

    @Mock
    private GitLabApiClient targetGitLabApiClient;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Mock
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Mock
    private SyncEventMapper syncEventMapper;

    @InjectMocks
    private TargetProjectManagementService targetProjectManagementService;

    private SourceProjectInfo sourceProjectInfo;
    private GitLabProject gitlabProject;

    @BeforeEach
    void setUp() {
        // 准备测试数据
        sourceProjectInfo = new SourceProjectInfo();
        sourceProjectInfo.setId(1L);
        sourceProjectInfo.setSyncProjectId(100L);
        sourceProjectInfo.setGitlabProjectId(1001L);
        sourceProjectInfo.setName("Test Project");
        sourceProjectInfo.setPathWithNamespace("group1/test-project");
        sourceProjectInfo.setGroupPath("group1");
        sourceProjectInfo.setVisibility("private");

        gitlabProject = new GitLabProject();
        gitlabProject.setId(2001L);
        gitlabProject.setName("Test Project");
        gitlabProject.setPath("test-project");
        gitlabProject.setPathWithNamespace("group1/test-project");
    }

    /**
     * 测试创建单个项目
     */
    @Test
    void testCreateTargetProject_SingleProject() {
        // Given: 准备源项目信息
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        // Mock: 目标项目不存在
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(targetProjectInfoMapper.insert(any(TargetProjectInfo.class)))
                .thenAnswer(invocation -> {
                    TargetProjectInfo info = invocation.getArgument(0);
                    info.setId(1L);
                    return 1;
                });

        // Mock: 分组存在
        when(targetGitLabApiClient.groupExists("group1"))
                .thenReturn(true);

        // Mock: 创建项目成功
        when(targetGitLabApiClient.createProject(eq("test-project"), eq("Test Project"), eq("group1")))
                .thenReturn(gitlabProject);

        // When: 创建目标项目
        TargetProjectInfo result = targetProjectManagementService.createTargetProject(100L);

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.getSyncProjectId()).isEqualTo(100L);
        assertThat(result.getGitlabProjectId()).isEqualTo(2001L);
        assertThat(result.getPathWithNamespace()).isEqualTo("group1/test-project");
        assertThat(result.getStatus()).isEqualTo(TargetProjectInfo.Status.CREATED);

        // 验证分组检查
        verify(targetGitLabApiClient).groupExists("group1");

        // 验证项目创建
        verify(targetGitLabApiClient).createProject("test-project", "Test Project", "group1");

        // 验证数据库操作
        verify(targetProjectInfoMapper).insert(any(TargetProjectInfo.class));
        verify(targetProjectInfoMapper).updateById(any(TargetProjectInfo.class));

        // 验证事件记录
        verify(syncEventMapper).insert(any(SyncEvent.class));
    }

    /**
     * 测试创建嵌套分组项目
     */
    @Test
    void testCreateTargetProject_NestedGroups() {
        // Given: 准备嵌套分组的源项目信息
        sourceProjectInfo.setPathWithNamespace("group1/subgroup1/subgroup2/test-project");
        sourceProjectInfo.setGroupPath("group1/subgroup1/subgroup2");

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(targetProjectInfoMapper.insert(any(TargetProjectInfo.class)))
                .thenAnswer(invocation -> {
                    TargetProjectInfo info = invocation.getArgument(0);
                    info.setId(1L);
                    return 1;
                });

        // Mock: 分组层级创建
        when(targetGitLabApiClient.groupExists("group1")).thenReturn(false);
        when(targetGitLabApiClient.groupExists("group1/subgroup1")).thenReturn(false);
        when(targetGitLabApiClient.groupExists("group1/subgroup1/subgroup2")).thenReturn(false);

        GitLabGroup group1 = new GitLabGroup();
        group1.setId(101L);
        group1.setPath("group1");
        when(targetGitLabApiClient.createGroup("group1", "Group1", null))
                .thenReturn(group1);

        GitLabGroup subgroup1 = new GitLabGroup();
        subgroup1.setId(102L);
        subgroup1.setPath("subgroup1");
        when(targetGitLabApiClient.createGroup("subgroup1", "Subgroup1", "group1"))
                .thenReturn(subgroup1);

        GitLabGroup subgroup2 = new GitLabGroup();
        subgroup2.setId(103L);
        subgroup2.setPath("subgroup2");
        when(targetGitLabApiClient.createGroup("subgroup2", "Subgroup2", "group1/subgroup1"))
                .thenReturn(subgroup2);

        // Mock: 创建项目成功
        gitlabProject.setPathWithNamespace("group1/subgroup1/subgroup2/test-project");
        when(targetGitLabApiClient.createProject(eq("test-project"), eq("Test Project"),
                eq("group1/subgroup1/subgroup2")))
                .thenReturn(gitlabProject);

        // When: 创建目标项目
        TargetProjectInfo result = targetProjectManagementService.createTargetProject(100L);

        // Then: 验证分组层级创建
        verify(targetGitLabApiClient).createGroup("group1", "Group1", null);
        verify(targetGitLabApiClient).createGroup("subgroup1", "Subgroup1", "group1");
        verify(targetGitLabApiClient).createGroup("subgroup2", "Subgroup2", "group1/subgroup1");

        // 验证项目创建
        verify(targetGitLabApiClient).createProject("test-project", "Test Project", "group1/subgroup1/subgroup2");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TargetProjectInfo.Status.CREATED);
    }

    /**
     * 测试处理已存在项目
     */
    @Test
    void testCreateTargetProject_ProjectAlreadyExists() {
        // Given: 目标项目已存在且状态为CREATED
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        TargetProjectInfo existingTarget = new TargetProjectInfo();
        existingTarget.setId(1L);
        existingTarget.setSyncProjectId(100L);
        existingTarget.setGitlabProjectId(2001L);
        existingTarget.setPathWithNamespace("group1/test-project");
        existingTarget.setStatus(TargetProjectInfo.Status.CREATED);
        existingTarget.setRetryCount(0);

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(existingTarget);

        // When: 尝试创建目标项目
        TargetProjectInfo result = targetProjectManagementService.createTargetProject(100L);

        // Then: 应该直接返回已存在的项目，不创建新项目
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(TargetProjectInfo.Status.CREATED);

        // 验证没有调用GitLab API创建项目
        verify(targetGitLabApiClient, never()).createProject(any(), any(), any());
    }

    /**
     * 测试重试机制
     */
    @Test
    void testCreateTargetProject_RetryAfterError() {
        // Given: 目标项目之前创建失败，状态为ERROR
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        TargetProjectInfo existingTarget = new TargetProjectInfo();
        existingTarget.setId(1L);
        existingTarget.setSyncProjectId(100L);
        existingTarget.setPathWithNamespace("group1/test-project");
        existingTarget.setStatus(TargetProjectInfo.Status.ERROR);
        existingTarget.setRetryCount(1);
        existingTarget.setErrorMessage("Previous error");

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(existingTarget);

        // Mock: 分组存在
        when(targetGitLabApiClient.groupExists("group1")).thenReturn(true);

        // Mock: 这次创建成功
        when(targetGitLabApiClient.createProject(eq("test-project"), eq("Test Project"), eq("group1")))
                .thenReturn(gitlabProject);

        // When: 重试创建目标项目
        TargetProjectInfo result = targetProjectManagementService.createTargetProject(100L);

        // Then: 验证重试成功
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TargetProjectInfo.Status.CREATED);
        assertThat(result.getRetryCount()).isEqualTo(2);
        assertThat(result.getErrorMessage()).isNull();

        // 验证更新了数据库
        ArgumentCaptor<TargetProjectInfo> captor = ArgumentCaptor.forClass(TargetProjectInfo.class);
        verify(targetProjectInfoMapper, atLeastOnce()).updateById(captor.capture());

        TargetProjectInfo updated = captor.getValue();
        assertThat(updated.getStatus()).isEqualTo(TargetProjectInfo.Status.CREATED);
    }

    /**
     * 测试创建失败时的错误处理
     */
    @Test
    void testCreateTargetProject_CreationFailed() {
        // Given: 准备源项目信息
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(targetProjectInfoMapper.insert(any(TargetProjectInfo.class)))
                .thenAnswer(invocation -> {
                    TargetProjectInfo info = invocation.getArgument(0);
                    info.setId(1L);
                    return 1;
                });

        // Mock: 分组存在
        when(targetGitLabApiClient.groupExists("group1")).thenReturn(true);

        // Mock: 创建项目失败
        when(targetGitLabApiClient.createProject(any(), any(), any()))
                .thenThrow(new GitLabClientException("API error", null));

        // When & Then: 创建失败应抛出异常
        assertThatThrownBy(() -> targetProjectManagementService.createTargetProject(100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create target project");

        // 验证错误状态被记录
        ArgumentCaptor<TargetProjectInfo> captor = ArgumentCaptor.forClass(TargetProjectInfo.class);
        verify(targetProjectInfoMapper, atLeastOnce()).updateById(captor.capture());

        TargetProjectInfo errorInfo = captor.getValue();
        assertThat(errorInfo.getStatus()).isEqualTo(TargetProjectInfo.Status.ERROR);
        assertThat(errorInfo.getErrorMessage()).contains("API error");

        // 验证错误事件被记录
        verify(syncEventMapper, atLeastOnce()).insert(any(SyncEvent.class));
    }

    /**
     * 测试状态检查 - 项目存在
     */
    @Test
    void testCheckTargetProjectStatus_ProjectExists() {
        // Given: 目标项目存在
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setId(1L);
        targetInfo.setSyncProjectId(100L);
        targetInfo.setPathWithNamespace("group1/test-project");
        targetInfo.setStatus(TargetProjectInfo.Status.CREATED);

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetInfo);
        when(targetGitLabApiClient.projectExists("group1/test-project"))
                .thenReturn(true);

        // When: 检查状态
        TargetProjectInfo result = targetProjectManagementService.checkTargetProjectStatus(100L);

        // Then: 状态应更新为READY
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TargetProjectInfo.Status.READY);
        assertThat(result.getLastCheckedAt()).isNotNull();

        verify(targetGitLabApiClient).projectExists("group1/test-project");
        verify(targetProjectInfoMapper).updateById(any(TargetProjectInfo.class));
    }

    /**
     * 测试状态检查 - 项目被删除
     */
    @Test
    void testCheckTargetProjectStatus_ProjectDeleted() {
        // Given: 目标项目被删除
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setId(1L);
        targetInfo.setSyncProjectId(100L);
        targetInfo.setPathWithNamespace("group1/test-project");
        targetInfo.setStatus(TargetProjectInfo.Status.READY);

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetInfo);
        when(targetGitLabApiClient.projectExists("group1/test-project"))
                .thenReturn(false);

        // When: 检查状态
        TargetProjectInfo result = targetProjectManagementService.checkTargetProjectStatus(100L);

        // Then: 状态应更新为DELETED
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(TargetProjectInfo.Status.DELETED);
        assertThat(result.getErrorMessage()).contains("deleted from target GitLab");

        // 验证事件记录
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper).insert(eventCaptor.capture());
        SyncEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("target_project_deleted");
        assertThat(event.getStatus()).isEqualTo("warning");
    }

    /**
     * 测试批量创建
     */
    @Test
    void testBatchCreateTargetProjects() {
        // Given: 准备多个同步项目
        List<Long> syncProjectIds = Arrays.asList(100L, 101L, 102L);

        // Mock source project info for each
        SourceProjectInfo source1 = createSourceProjectInfo(100L, "group1/project1");
        SourceProjectInfo source2 = createSourceProjectInfo(101L, "group1/project2");
        SourceProjectInfo source3 = createSourceProjectInfo(102L, "group1/project3");

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(source1, source2, source3);

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(targetProjectInfoMapper.insert(any(TargetProjectInfo.class)))
                .thenAnswer(invocation -> {
                    TargetProjectInfo info = invocation.getArgument(0);
                    info.setId(System.currentTimeMillis());
                    return 1;
                });

        when(targetGitLabApiClient.groupExists(any())).thenReturn(true);

        // Mock project creation
        GitLabProject project1 = createGitLabProject(2001L, "group1/project1");
        GitLabProject project2 = createGitLabProject(2002L, "group1/project2");
        GitLabProject project3 = createGitLabProject(2003L, "group1/project3");

        when(targetGitLabApiClient.createProject(eq("project1"), any(), eq("group1")))
                .thenReturn(project1);
        when(targetGitLabApiClient.createProject(eq("project2"), any(), eq("group1")))
                .thenReturn(project2);
        when(targetGitLabApiClient.createProject(eq("project3"), any(), eq("group1")))
                .thenReturn(project3);

        // When: 批量创建
        List<TargetProjectInfo> results = targetProjectManagementService.batchCreateTargetProjects(syncProjectIds);

        // Then: 验证结果
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(info -> info.getStatus().equals(TargetProjectInfo.Status.CREATED));

        // 验证所有项目都被创建
        verify(targetGitLabApiClient).createProject(eq("project1"), any(), eq("group1"));
        verify(targetGitLabApiClient).createProject(eq("project2"), any(), eq("group1"));
        verify(targetGitLabApiClient).createProject(eq("project3"), any(), eq("group1"));
    }

    /**
     * 测试批量检查状态
     */
    @Test
    void testBatchCheckTargetProjectStatus() {
        // Given: 准备多个目标项目
        List<Long> syncProjectIds = Arrays.asList(100L, 101L, 102L);

        TargetProjectInfo target1 = createTargetProjectInfo(100L, "group1/project1", TargetProjectInfo.Status.CREATED);
        TargetProjectInfo target2 = createTargetProjectInfo(101L, "group1/project2", TargetProjectInfo.Status.CREATED);
        TargetProjectInfo target3 = createTargetProjectInfo(102L, "group1/project3", TargetProjectInfo.Status.CREATED);

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(target1, target2, target3);

        // Mock: 项目都存在
        when(targetGitLabApiClient.projectExists(any())).thenReturn(true);

        // When: 批量检查状态
        List<TargetProjectInfo> results = targetProjectManagementService.batchCheckTargetProjectStatus(syncProjectIds);

        // Then: 验证结果
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(info -> info.getStatus().equals(TargetProjectInfo.Status.READY));

        // 验证所有项目状态都被检查
        verify(targetGitLabApiClient).projectExists("group1/project1");
        verify(targetGitLabApiClient).projectExists("group1/project2");
        verify(targetGitLabApiClient).projectExists("group1/project3");
    }

    // Helper methods

    private SourceProjectInfo createSourceProjectInfo(Long syncProjectId, String pathWithNamespace) {
        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setPathWithNamespace(pathWithNamespace);
        info.setName("Project " + syncProjectId);
        info.setVisibility("private");

        int lastSlash = pathWithNamespace.lastIndexOf('/');
        if (lastSlash > 0) {
            info.setGroupPath(pathWithNamespace.substring(0, lastSlash));
        }

        return info;
    }

    private GitLabProject createGitLabProject(Long id, String pathWithNamespace) {
        GitLabProject project = new GitLabProject();
        project.setId(id);
        project.setPathWithNamespace(pathWithNamespace);

        int lastSlash = pathWithNamespace.lastIndexOf('/');
        if (lastSlash > 0) {
            project.setPath(pathWithNamespace.substring(lastSlash + 1));
        } else {
            project.setPath(pathWithNamespace);
        }

        return project;
    }

    private TargetProjectInfo createTargetProjectInfo(Long syncProjectId, String pathWithNamespace, String status) {
        TargetProjectInfo info = new TargetProjectInfo();
        info.setId(syncProjectId);
        info.setSyncProjectId(syncProjectId);
        info.setPathWithNamespace(pathWithNamespace);
        info.setStatus(status);
        return info;
    }
}
