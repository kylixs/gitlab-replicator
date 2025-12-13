package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProjectDiscoveryService
 *
 * 测试要求：
 * - 测试首次发现项目
 * - 测试增量发现
 * - 测试更新已存在项目
 * - 测试过滤规则
 * - 测试定时调度
 * - 测试大量项目场景
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class ProjectDiscoveryServiceTest {

    @Mock
    private GitLabApiClient sourceGitLabApiClient;

    @Mock
    private SyncProjectMapper syncProjectMapper;

    @Mock
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Mock
    private SyncEventMapper syncEventMapper;

    @Mock
    private GitLabMirrorProperties properties;

    @Mock
    private GitLabMirrorProperties.SyncConfig syncConfig;

    @InjectMocks
    private ProjectDiscoveryService projectDiscoveryService;

    @BeforeEach
    void setUp() {
        when(properties.getSync()).thenReturn(syncConfig);
        lenient().when(syncConfig.getEnabled()).thenReturn(true);
        lenient().when(syncConfig.getExcludeArchived()).thenReturn(true);
        lenient().when(syncConfig.getExcludeEmpty()).thenReturn(true);
    }

    /**
     * 测试首次发现项目
     */
    @Test
    void testDiscoverProjects_FirstTimeDiscovery() {
        // Given: 准备GitLab项目数据
        GitLabProject gitlabProject = createGitLabProject(1L, "group1/project1", false, false);
        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(List.of(gitlabProject));

        // Mock: 项目不存在
        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(1L);
                    return 1;
                });

        // Mock: 源项目信息不存在
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 验证结果
        assertThat(count).isEqualTo(1);

        // 验证SyncProject被创建
        ArgumentCaptor<SyncProject> syncProjectCaptor = ArgumentCaptor.forClass(SyncProject.class);
        verify(syncProjectMapper).insert(syncProjectCaptor.capture());
        SyncProject savedProject = syncProjectCaptor.getValue();
        assertThat(savedProject.getProjectKey()).isEqualTo("group1/project1");
        assertThat(savedProject.getSyncMethod()).isEqualTo(SyncProject.SyncMethod.PUSH_MIRROR);
        assertThat(savedProject.getSyncStatus()).isEqualTo(SyncProject.SyncStatus.PENDING);
        assertThat(savedProject.getEnabled()).isTrue();

        // 验证SourceProjectInfo被创建
        ArgumentCaptor<SourceProjectInfo> infoCaptor = ArgumentCaptor.forClass(SourceProjectInfo.class);
        verify(sourceProjectInfoMapper).insert(infoCaptor.capture());
        SourceProjectInfo savedInfo = infoCaptor.getValue();
        assertThat(savedInfo.getGitlabProjectId()).isEqualTo(1L);
        assertThat(savedInfo.getName()).isEqualTo("project1");
        assertThat(savedInfo.getPathWithNamespace()).isEqualTo("group1/project1");

        // 验证事件被记录
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper).insert(eventCaptor.capture());
        SyncEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getEventType()).isEqualTo("project_discovered");
        assertThat(savedEvent.getStatus()).isEqualTo("success");
    }

    /**
     * 测试增量发现（新增项目）
     */
    @Test
    void testDiscoverProjects_IncrementalDiscovery() {
        // Given: 已存在一个项目，新增一个项目
        GitLabProject existingProject = createGitLabProject(1L, "group1/project1", false, false);
        GitLabProject newProject = createGitLabProject(2L, "group1/project2", false, false);

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(Arrays.asList(existingProject, newProject));

        // Mock: 第一个项目存在，第二个不存在
        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(createSyncProject(1L, "group1/project1"))  // 第一个项目存在
                .thenReturn(null);  // 第二个项目不存在

        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(2L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(createSourceProjectInfo(1L, 1L))  // 第一个项目信息存在
                .thenReturn(null);  // 第二个项目信息不存在

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 验证结果
        assertThat(count).isEqualTo(2);

        // 验证只有新项目被插入
        verify(syncProjectMapper, times(1)).insert(any(SyncProject.class));

        // 验证已存在项目被更新
        verify(syncProjectMapper, times(1)).updateById(any(SyncProject.class));

        // 验证两个事件都被记录
        verify(syncEventMapper, times(2)).insert(any(SyncEvent.class));
    }

    /**
     * 测试更新已存在项目
     */
    @Test
    void testDiscoverProjects_UpdateExistingProject() {
        // Given: 准备已存在的项目
        GitLabProject gitlabProject = createGitLabProject(1L, "group1/project1", false, false);
        gitlabProject.setStarCount(10);
        gitlabProject.setForksCount(5);

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(List.of(gitlabProject));

        // Mock: 项目已存在
        SyncProject existingProject = createSyncProject(1L, "group1/project1");
        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(existingProject);

        // Mock: 源项目信息已存在
        SourceProjectInfo existingInfo = createSourceProjectInfo(1L, 1L);
        existingInfo.setStarCount(5);  // 旧的star数
        existingInfo.setForkCount(2);  // 旧的fork数
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(existingInfo);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 验证结果
        assertThat(count).isEqualTo(1);

        // 验证SyncProject被更新
        verify(syncProjectMapper).updateById(any(SyncProject.class));
        verify(syncProjectMapper, never()).insert(any(SyncProject.class));

        // 验证SourceProjectInfo被更新
        ArgumentCaptor<SourceProjectInfo> infoCaptor = ArgumentCaptor.forClass(SourceProjectInfo.class);
        verify(sourceProjectInfoMapper).updateById(infoCaptor.capture());
        SourceProjectInfo updatedInfo = infoCaptor.getValue();
        assertThat(updatedInfo.getStarCount()).isEqualTo(10);
        assertThat(updatedInfo.getForkCount()).isEqualTo(5);

        // 验证更新事件被记录
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper).insert(eventCaptor.capture());
        SyncEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("project_updated");
    }

    /**
     * 测试过滤规则 - 排除归档项目
     */
    @Test
    void testDiscoverProjects_FilterArchivedProjects() {
        // Given: 准备一个归档项目和一个正常项目
        GitLabProject archivedProject = createGitLabProject(1L, "group1/archived", true, false);
        GitLabProject normalProject = createGitLabProject(2L, "group1/normal", false, false);

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(Arrays.asList(archivedProject, normalProject));

        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(2L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 只有正常项目被处理
        assertThat(count).isEqualTo(1);

        // 验证只插入了一个项目
        ArgumentCaptor<SyncProject> captor = ArgumentCaptor.forClass(SyncProject.class);
        verify(syncProjectMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getProjectKey()).isEqualTo("group1/normal");
    }

    /**
     * 测试过滤规则 - 排除空仓库
     */
    @Test
    void testDiscoverProjects_FilterEmptyRepositories() {
        // Given: 准备一个空仓库和一个正常项目
        GitLabProject emptyProject = createGitLabProject(1L, "group1/empty", false, true);
        GitLabProject normalProject = createGitLabProject(2L, "group1/normal", false, false);

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(Arrays.asList(emptyProject, normalProject));

        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(2L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 只有正常项目被处理
        assertThat(count).isEqualTo(1);

        // 验证只插入了一个项目
        ArgumentCaptor<SyncProject> captor = ArgumentCaptor.forClass(SyncProject.class);
        verify(syncProjectMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getProjectKey()).isEqualTo("group1/normal");
    }

    /**
     * 测试过滤规则 - 不排除归档项目（配置disabled）
     */
    @Test
    void testDiscoverProjects_DoNotFilterWhenDisabled() {
        // Given: 配置不排除归档项目
        when(syncConfig.getExcludeArchived()).thenReturn(false);
        when(syncConfig.getExcludeEmpty()).thenReturn(false);

        GitLabProject archivedProject = createGitLabProject(1L, "group1/archived", true, false);
        GitLabProject emptyProject = createGitLabProject(2L, "group1/empty", false, true);

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(Arrays.asList(archivedProject, emptyProject));

        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(1L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 两个项目都被处理
        assertThat(count).isEqualTo(2);
        verify(syncProjectMapper, times(2)).insert(any(SyncProject.class));
    }

    /**
     * 测试定时调度 - 同步已启用
     */
    @Test
    void testScheduleDiscovery_WhenSyncEnabled() {
        // Given: 同步已启用
        when(syncConfig.getEnabled()).thenReturn(true);

        GitLabProject project = createGitLabProject(1L, "group1/project1", false, false);
        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(List.of(project));

        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject p = invocation.getArgument(0);
                    p.setId(1L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行定时任务
        projectDiscoveryService.scheduleDiscovery();

        // Then: 项目发现被执行
        verify(sourceGitLabApiClient).getAllProjects(null);
        verify(syncProjectMapper).insert(any(SyncProject.class));
    }

    /**
     * 测试定时调度 - 同步已禁用
     */
    @Test
    void testScheduleDiscovery_WhenSyncDisabled() {
        // Given: 同步已禁用
        when(syncConfig.getEnabled()).thenReturn(false);

        // When: 执行定时任务
        projectDiscoveryService.scheduleDiscovery();

        // Then: 项目发现不被执行
        verify(sourceGitLabApiClient, never()).getAllProjects(any());
        verify(syncProjectMapper, never()).insert(any(SyncProject.class));
    }

    /**
     * 测试大量项目场景
     */
    @Test
    void testDiscoverProjects_LargeNumberOfProjects() {
        // Given: 准备100个项目
        List<GitLabProject> projects = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            projects.add(createGitLabProject((long) i, "group1/project" + i, false, false));
        }

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(projects);

        // Mock: 所有项目都不存在
        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);
        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(1L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 所有项目都被处理
        assertThat(count).isEqualTo(100);
        verify(syncProjectMapper, times(100)).insert(any(SyncProject.class));
        verify(sourceProjectInfoMapper, times(100)).insert(any(SourceProjectInfo.class));
        verify(syncEventMapper, times(100)).insert(any(SyncEvent.class));
    }

    /**
     * 测试处理单个项目失败不影响其他项目
     */
    @Test
    void testDiscoverProjects_OneProjectFailsOthersContinue() {
        // Given: 准备3个项目
        GitLabProject project1 = createGitLabProject(1L, "group1/project1", false, false);
        GitLabProject project2 = createGitLabProject(2L, "group1/project2", false, false);
        GitLabProject project3 = createGitLabProject(3L, "group1/project3", false, false);

        when(sourceGitLabApiClient.getAllProjects(null))
                .thenReturn(Arrays.asList(project1, project2, project3));

        // Mock: 第二个项目处理时抛出异常
        when(syncProjectMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null)
                .thenThrow(new RuntimeException("Database error"))
                .thenReturn(null);

        when(syncProjectMapper.insert(any(SyncProject.class)))
                .thenAnswer(invocation -> {
                    SyncProject project = invocation.getArgument(0);
                    project.setId(1L);
                    return 1;
                });

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null);

        // When: 执行项目发现
        int count = projectDiscoveryService.discoverProjects(null);

        // Then: 其他项目仍然被处理
        assertThat(count).isEqualTo(3);

        // 验证只有2个项目被成功插入（第二个失败了）
        verify(syncProjectMapper, times(2)).insert(any(SyncProject.class));
    }

    // ==================== Helper Methods ====================

    private GitLabProject createGitLabProject(Long id, String pathWithNamespace,
                                              Boolean archived, Boolean emptyRepo) {
        GitLabProject project = new GitLabProject();
        project.setId(id);
        project.setPathWithNamespace(pathWithNamespace);

        // Extract name from path
        String name = pathWithNamespace.substring(pathWithNamespace.lastIndexOf('/') + 1);
        project.setName(name);
        project.setPath(name);

        project.setArchived(archived);
        project.setEmptyRepo(emptyRepo);
        project.setVisibility("private");
        project.setDefaultBranch("main");
        project.setStarCount(0);
        project.setForksCount(0);
        project.setWebUrl("http://gitlab.com/" + pathWithNamespace);
        project.setSshUrlToRepo("git@gitlab.com:" + pathWithNamespace + ".git");
        project.setHttpUrlToRepo("http://gitlab.com/" + pathWithNamespace + ".git");
        project.setCreatedAt(OffsetDateTime.now());
        project.setLastActivityAt(OffsetDateTime.now());

        return project;
    }

    private SyncProject createSyncProject(Long id, String projectKey) {
        SyncProject project = new SyncProject();
        project.setId(id);
        project.setProjectKey(projectKey);
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        return project;
    }

    private SourceProjectInfo createSourceProjectInfo(Long id, Long syncProjectId) {
        SourceProjectInfo info = new SourceProjectInfo();
        info.setId(id);
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(1L);
        info.setName("project1");
        info.setPathWithNamespace("group1/project1");
        info.setDefaultBranch("main");
        info.setVisibility("private");
        info.setArchived(false);
        info.setEmptyRepo(false);
        info.setStarCount(0);
        info.setForkCount(0);
        return info;
    }
}
