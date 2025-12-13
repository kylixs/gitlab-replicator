package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.common.model.RemoteMirror;
import com.gitlab.mirror.server.client.GitLabApiClient;
import com.gitlab.mirror.server.client.GitLabClientException;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.PushMirrorConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncEventMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PushMirrorManagementService
 *
 * 测试要求：
 * - 测试 Mirror 配置流程
 * - 测试 URL 构建（Token 安全）
 * - 测试批量配置
 * - 测试并发控制
 * - 测试状态轮询
 * - 测试状态变化检测
 * - 测试定时调度
 *
 * @author GitLab Mirror Team
 */
@ExtendWith(MockitoExtension.class)
class PushMirrorManagementServiceTest {

    @Mock
    private GitLabApiClient sourceGitLabApiClient;

    @Mock
    private GitLabApiClient targetGitLabApiClient;

    @Mock
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @Mock
    private TargetProjectInfoMapper targetProjectInfoMapper;

    @Mock
    private PushMirrorConfigMapper pushMirrorConfigMapper;

    @Mock
    private SyncEventMapper syncEventMapper;

    @Mock
    private GitLabMirrorProperties properties;

    @Mock
    private GitLabMirrorProperties.GitLabInstance targetInstance;

    @Mock
    private GitLabMirrorProperties.SyncConfig syncConfig;

    @Mock
    private TargetProjectManagementService targetProjectManagementService;

    @InjectMocks
    private PushMirrorManagementService pushMirrorManagementService;

    private SourceProjectInfo sourceProjectInfo;
    private TargetProjectInfo targetProjectInfo;
    private RemoteMirror remoteMirror;

    @BeforeEach
    void setUp() {
        // Setup properties
        lenient().when(properties.getTarget()).thenReturn(targetInstance);
        lenient().when(properties.getSync()).thenReturn(syncConfig);
        lenient().when(syncConfig.getEnabled()).thenReturn(true);
        lenient().when(targetInstance.getUrl()).thenReturn("http://localhost:9000");
        lenient().when(targetInstance.getToken()).thenReturn("test-token-123");

        // Setup test data
        sourceProjectInfo = new SourceProjectInfo();
        sourceProjectInfo.setId(1L);
        sourceProjectInfo.setSyncProjectId(100L);
        sourceProjectInfo.setGitlabProjectId(1001L);
        sourceProjectInfo.setPathWithNamespace("group1/project1");

        targetProjectInfo = new TargetProjectInfo();
        targetProjectInfo.setId(1L);
        targetProjectInfo.setSyncProjectId(100L);
        targetProjectInfo.setGitlabProjectId(2001L);
        targetProjectInfo.setPathWithNamespace("group1/project1");

        remoteMirror = new RemoteMirror();
        remoteMirror.setId(3001L);
        remoteMirror.setEnabled(true);
        remoteMirror.setUpdateStatus("finished");
        remoteMirror.setUrl("http://localhost:9000/group1/project1.git");
        remoteMirror.setLastUpdateAt(OffsetDateTime.now());
    }

    /**
     * 测试 Mirror 配置流程
     */
    @Test
    void testConfigureMirror_Success() {
        // Given: 准备源项目和目标项目信息
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetProjectInfo);

        // Mock: Mirror配置不存在
        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(null);
        when(pushMirrorConfigMapper.insert(any(PushMirrorConfig.class)))
                .thenAnswer(invocation -> {
                    PushMirrorConfig config = invocation.getArgument(0);
                    config.setId(1L);
                    return 1;
                });

        // Mock: 创建Mirror成功
        when(sourceGitLabApiClient.createMirror(anyLong(), anyString(), anyBoolean()))
                .thenReturn(remoteMirror);

        // When: 配置Mirror
        PushMirrorConfig result = pushMirrorManagementService.configureMirror(100L);

        // Then: 验证结果
        assertThat(result).isNotNull();
        assertThat(result.getSyncProjectId()).isEqualTo(100L);
        assertThat(result.getGitlabMirrorId()).isEqualTo(3001L);
        assertThat(result.getMirrorUrl()).isEqualTo("http://localhost:9000/group1/project1.git");
        assertThat(result.getLastUpdateStatus()).isEqualTo("finished");

        // 验证Mirror创建调用
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> projectIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Boolean> branchCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sourceGitLabApiClient).createMirror(projectIdCaptor.capture(), urlCaptor.capture(), branchCaptor.capture());

        // 验证URL包含Token但不泄露
        String mirrorUrlWithToken = urlCaptor.getValue();
        assertThat(mirrorUrlWithToken).contains("oauth2:");
        assertThat(mirrorUrlWithToken).contains("test-token-123");
        assertThat(mirrorUrlWithToken).contains("@localhost:9000/group1/project1.git");

        // 验证触发首次同步
        verify(sourceGitLabApiClient).triggerMirrorSync(1001L, 3001L);

        // 验证事件记录
        verify(syncEventMapper).insert(any(SyncEvent.class));
    }

    /**
     * 测试 URL 构建（Token 安全）
     */
    @Test
    void testBuildMirrorUrl_TokenSafety() {
        // Given
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetProjectInfo);
        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(null);
        when(pushMirrorConfigMapper.insert(any(PushMirrorConfig.class)))
                .thenAnswer(invocation -> {
                    PushMirrorConfig config = invocation.getArgument(0);
                    config.setId(1L);
                    return 1;
                });
        when(sourceGitLabApiClient.createMirror(anyLong(), anyString(), anyBoolean()))
                .thenReturn(remoteMirror);

        // When
        PushMirrorConfig result = pushMirrorManagementService.configureMirror(100L);

        // Then: 验证存储的URL不包含Token
        assertThat(result.getMirrorUrl()).doesNotContain("oauth2:");
        assertThat(result.getMirrorUrl()).doesNotContain("test-token-123");
        assertThat(result.getMirrorUrl()).isEqualTo("http://localhost:9000/group1/project1.git");

        // 验证API调用时使用的URL包含Token
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> projectIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Boolean> branchCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(sourceGitLabApiClient).createMirror(projectIdCaptor.capture(), urlCaptor.capture(), branchCaptor.capture());
        String apiUrl = urlCaptor.getValue();
        assertThat(apiUrl).contains("oauth2:");
        assertThat(apiUrl).contains("test-token-123");
    }

    /**
     * 测试处理已存在的Mirror
     */
    @Test
    void testConfigureMirror_AlreadyExists() {
        // Given: Mirror配置已存在
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetProjectInfo);

        PushMirrorConfig existingConfig = new PushMirrorConfig();
        existingConfig.setId(1L);
        existingConfig.setSyncProjectId(100L);
        existingConfig.setGitlabMirrorId(3001L);
        existingConfig.setMirrorUrl("http://localhost:9000/group1/project1.git");

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(existingConfig);

        // When: 尝试配置Mirror
        PushMirrorConfig result = pushMirrorManagementService.configureMirror(100L);

        // Then: 应该直接返回已存在的配置，不创建新Mirror
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getGitlabMirrorId()).isEqualTo(3001L);

        // 验证没有调用GitLab API创建Mirror
        verify(sourceGitLabApiClient, never()).createMirror(anyLong(), anyString(), anyBoolean());
    }

    /**
     * 测试配置失败
     */
    @Test
    void testConfigureMirror_Failure() {
        // Given
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetProjectInfo);
        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(null);
        when(pushMirrorConfigMapper.insert(any(PushMirrorConfig.class)))
                .thenAnswer(invocation -> {
                    PushMirrorConfig config = invocation.getArgument(0);
                    config.setId(1L);
                    return 1;
                });

        // Mock: 创建Mirror失败
        when(sourceGitLabApiClient.createMirror(anyLong(), anyString(), anyBoolean()))
                .thenThrow(new GitLabClientException("API error", null));

        // When & Then: 应该抛出异常
        assertThatThrownBy(() -> pushMirrorManagementService.configureMirror(100L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to configure push mirror");

        // 验证错误状态被记录
        ArgumentCaptor<PushMirrorConfig> captor = ArgumentCaptor.forClass(PushMirrorConfig.class);
        verify(pushMirrorConfigMapper, atLeastOnce()).updateById(captor.capture());
        PushMirrorConfig errorConfig = captor.getValue();
        assertThat(errorConfig.getErrorMessage()).contains("API error");
        assertThat(errorConfig.getConsecutiveFailures()).isEqualTo(1);

        // 验证错误事件被记录
        verify(syncEventMapper, atLeastOnce()).insert(any(SyncEvent.class));
    }

    /**
     * 测试批量配置
     */
    @Test
    void testBatchConfigureMirrors() {
        // Given: 准备多个同步项目
        List<Long> syncProjectIds = Arrays.asList(100L, 101L, 102L);

        // Setup multiple source and target projects
        SourceProjectInfo source1 = createSourceProjectInfo(100L, 1001L, "group1/project1");
        SourceProjectInfo source2 = createSourceProjectInfo(101L, 1002L, "group1/project2");
        SourceProjectInfo source3 = createSourceProjectInfo(102L, 1003L, "group1/project3");

        TargetProjectInfo target1 = createTargetProjectInfo(100L, 2001L, "group1/project1");
        TargetProjectInfo target2 = createTargetProjectInfo(101L, 2002L, "group1/project2");
        TargetProjectInfo target3 = createTargetProjectInfo(102L, 2003L, "group1/project3");

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(source1, source2, source3);
        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(target1, target2, target3);

        when(pushMirrorConfigMapper.selectBySyncProjectId(any()))
                .thenReturn(null);
        when(pushMirrorConfigMapper.insert(any(PushMirrorConfig.class)))
                .thenAnswer(invocation -> {
                    PushMirrorConfig config = invocation.getArgument(0);
                    config.setId(System.currentTimeMillis());
                    return 1;
                });

        // Mock mirror creation
        RemoteMirror mirror1 = createRemoteMirror(3001L, "finished");
        RemoteMirror mirror2 = createRemoteMirror(3002L, "finished");
        RemoteMirror mirror3 = createRemoteMirror(3003L, "finished");

        when(sourceGitLabApiClient.createMirror(anyLong(), anyString(), anyBoolean())).thenReturn(mirror1, mirror2, mirror3);

        // When: 批量配置
        List<PushMirrorConfig> results = pushMirrorManagementService.batchConfigureMirrors(syncProjectIds);

        // Then: 验证结果
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(config -> config.getGitlabMirrorId() != null);

        // 验证所有Mirror都被创建
        verify(sourceGitLabApiClient, times(3)).createMirror(anyLong(), anyString(), anyBoolean());
    }

    /**
     * 测试状态轮询
     */
    @Test
    void testPollMirrorStatus_StatusChanged() {
        // Given: 准备Mirror配置
        PushMirrorConfig config = new PushMirrorConfig();
        config.setId(1L);
        config.setSyncProjectId(100L);
        config.setGitlabMirrorId(3001L);
        config.setLastUpdateStatus("pending");
        config.setConsecutiveFailures(0);

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(config);
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        // Mock: Mirror状态变化为finished
        remoteMirror.setUpdateStatus("finished");
        when(sourceGitLabApiClient.getMirror(1001L, 3001L))
                .thenReturn(remoteMirror);

        // When: 轮询状态
        boolean statusChanged = pushMirrorManagementService.pollMirrorStatus(100L);

        // Then: 验证状态变化被检测到
        assertThat(statusChanged).isTrue();

        // 验证配置被更新
        ArgumentCaptor<PushMirrorConfig> captor = ArgumentCaptor.forClass(PushMirrorConfig.class);
        verify(pushMirrorConfigMapper).updateById(captor.capture());
        PushMirrorConfig updatedConfig = captor.getValue();
        assertThat(updatedConfig.getLastUpdateStatus()).isEqualTo("finished");
        assertThat(updatedConfig.getConsecutiveFailures()).isEqualTo(0);

        // 验证事件被记录
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper).insert(eventCaptor.capture());
        SyncEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("mirror_status_changed");
    }

    /**
     * 测试状态轮询 - 失败计数
     */
    @Test
    void testPollMirrorStatus_FailureCount() {
        // Given: Mirror状态为pending
        PushMirrorConfig config = new PushMirrorConfig();
        config.setId(1L);
        config.setSyncProjectId(100L);
        config.setGitlabMirrorId(3001L);
        config.setLastUpdateStatus("pending");
        config.setConsecutiveFailures(0);

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(config);
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        // Mock: Mirror状态变化为failed
        remoteMirror.setUpdateStatus("failed");
        remoteMirror.setLastError("Connection timeout");
        when(sourceGitLabApiClient.getMirror(1001L, 3001L))
                .thenReturn(remoteMirror);

        // When: 轮询状态
        boolean statusChanged = pushMirrorManagementService.pollMirrorStatus(100L);

        // Then: 验证失败计数增加
        assertThat(statusChanged).isTrue();

        ArgumentCaptor<PushMirrorConfig> captor = ArgumentCaptor.forClass(PushMirrorConfig.class);
        verify(pushMirrorConfigMapper).updateById(captor.capture());
        PushMirrorConfig updatedConfig = captor.getValue();
        assertThat(updatedConfig.getLastUpdateStatus()).isEqualTo("failed");
        assertThat(updatedConfig.getConsecutiveFailures()).isEqualTo(1);
        assertThat(updatedConfig.getErrorMessage()).isEqualTo("Connection timeout");
    }

    /**
     * 测试批量轮询
     */
    @Test
    void testBatchPollMirrorStatus() {
        // Given: 准备多个Mirror配置
        List<Long> syncProjectIds = Arrays.asList(100L, 101L, 102L);

        PushMirrorConfig config1 = createMirrorConfig(100L, 3001L, "pending");
        PushMirrorConfig config2 = createMirrorConfig(101L, 3002L, "finished");
        PushMirrorConfig config3 = createMirrorConfig(102L, 3003L, "pending");

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L)).thenReturn(config1);
        when(pushMirrorConfigMapper.selectBySyncProjectId(101L)).thenReturn(config2);
        when(pushMirrorConfigMapper.selectBySyncProjectId(102L)).thenReturn(config3);

        SourceProjectInfo source1 = createSourceProjectInfo(100L, 1001L, "group1/project1");
        SourceProjectInfo source2 = createSourceProjectInfo(101L, 1002L, "group1/project2");
        SourceProjectInfo source3 = createSourceProjectInfo(102L, 1003L, "group1/project3");

        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(source1, source2, source3);

        // Mock: 状态变化（100和102变化，101不变）
        RemoteMirror mirror1 = createRemoteMirror(3001L, "finished");
        RemoteMirror mirror2 = createRemoteMirror(3002L, "finished");
        RemoteMirror mirror3 = createRemoteMirror(3003L, "started");

        when(sourceGitLabApiClient.getMirror(1001L, 3001L)).thenReturn(mirror1);
        when(sourceGitLabApiClient.getMirror(1002L, 3002L)).thenReturn(mirror2);
        when(sourceGitLabApiClient.getMirror(1003L, 3003L)).thenReturn(mirror3);

        // When: 批量轮询
        int changedCount = pushMirrorManagementService.batchPollMirrorStatus(syncProjectIds);

        // Then: 验证2个状态变化
        assertThat(changedCount).isEqualTo(2);

        // 验证所有状态都被检查
        verify(sourceGitLabApiClient).getMirror(1001L, 3001L);
        verify(sourceGitLabApiClient).getMirror(1002L, 3002L);
        verify(sourceGitLabApiClient).getMirror(1003L, 3003L);
    }

    /**
     * 测试定时调度
     */
    @Test
    void testScheduledStatusPolling() {
        // Given: 准备Mirror配置列表
        PushMirrorConfig config1 = createMirrorConfig(100L, 3001L, "pending");
        PushMirrorConfig config2 = createMirrorConfig(101L, 3002L, "started");

        when(pushMirrorConfigMapper.selectList(null))
                .thenReturn(Arrays.asList(config1, config2));

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L)).thenReturn(config1);
        when(pushMirrorConfigMapper.selectBySyncProjectId(101L)).thenReturn(config2);

        SourceProjectInfo source1 = createSourceProjectInfo(100L, 1001L, "group1/project1");
        SourceProjectInfo source2 = createSourceProjectInfo(101L, 1002L, "group1/project2");

        // selectOne is called twice per mirror (once for config select, once for source)
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(source1)
                .thenReturn(source2);

        RemoteMirror mirror1 = createRemoteMirror(3001L, "finished");
        RemoteMirror mirror2 = createRemoteMirror(3002L, "finished");

        when(sourceGitLabApiClient.getMirror(1001L, 3001L)).thenReturn(mirror1);
        when(sourceGitLabApiClient.getMirror(1002L, 3002L)).thenReturn(mirror2);

        // When: 执行定时调度
        pushMirrorManagementService.scheduledStatusPolling();

        // Then: 验证所有Mirror状态都被检查
        verify(pushMirrorConfigMapper).selectList(null);
        verify(sourceGitLabApiClient).getMirror(1001L, 3001L);
        verify(sourceGitLabApiClient).getMirror(1002L, 3002L);
    }

    /**
     * 测试定时调度 - 禁用时跳过
     */
    @Test
    void testScheduledStatusPolling_Disabled() {
        // Given: Sync被禁用
        when(syncConfig.getEnabled()).thenReturn(false);

        // When: 执行定时调度
        pushMirrorManagementService.scheduledStatusPolling();

        // Then: 应该跳过轮询
        verify(pushMirrorConfigMapper, never()).selectList(any());
        verify(sourceGitLabApiClient, never()).getMirror(any(), any());
    }

    /**
     * 测试状态轮询 - 自动创建缺失的目标项目
     */
    @Test
    void testPollMirrorStatus_AutoCreateTargetProjectOnNotFoundError() {
        // Given: Mirror配置存在，但目标项目未创建
        PushMirrorConfig config = new PushMirrorConfig();
        config.setId(1L);
        config.setSyncProjectId(100L);
        config.setGitlabMirrorId(3001L);
        config.setLastUpdateStatus("failed");
        config.setConsecutiveFailures(1);

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(config);
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        // Mock: 目标项目信息存在但状态不是CREATED
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setId(1L);
        targetInfo.setSyncProjectId(100L);
        targetInfo.setStatus(TargetProjectInfo.Status.NOT_EXIST);
        targetInfo.setPathWithNamespace("group1/project1");

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetInfo);

        // Mock: Mirror返回"not found"错误
        remoteMirror.setUpdateStatus("failed");
        remoteMirror.setLastError("remote: The project you were looking for could not be found or you don't have permission to view it.\n" +
                "fatal: repository 'http://host.docker.internal:9000/devops/gitlab-mirror.git/' not found");
        when(sourceGitLabApiClient.getMirror(1001L, 3001L))
                .thenReturn(remoteMirror);

        // Mock: 自动创建目标项目成功
        TargetProjectInfo createdTarget = new TargetProjectInfo();
        createdTarget.setId(2L);
        createdTarget.setSyncProjectId(100L);
        createdTarget.setStatus(TargetProjectInfo.Status.CREATED);
        createdTarget.setGitlabProjectId(2001L);
        when(targetProjectManagementService.createTargetProject(100L)).thenReturn(createdTarget);

        // Mock: 触发同步成功
        doNothing().when(sourceGitLabApiClient).triggerMirrorSync(1001L, 3001L);

        // When: 轮询状态
        boolean statusChanged = pushMirrorManagementService.pollMirrorStatus(100L);

        // Then: 状态未变化（因为镜像仍然处于failed状态）
        assertThat(statusChanged).isFalse();

        // 验证目标项目被自动创建
        verify(targetProjectManagementService).createTargetProject(100L);

        // 验证同步被触发
        verify(sourceGitLabApiClient).triggerMirrorSync(1001L, 3001L);

        // 验证配置被更新
        ArgumentCaptor<PushMirrorConfig> configCaptor = ArgumentCaptor.forClass(PushMirrorConfig.class);
        verify(pushMirrorConfigMapper).updateById(configCaptor.capture());
        PushMirrorConfig updatedConfig = configCaptor.getValue();
        assertThat(updatedConfig.getLastUpdateStatus()).isEqualTo("failed");
        assertThat(updatedConfig.getErrorMessage()).contains("not found");

        // 验证成功事件被记录
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper, atLeastOnce()).insert(eventCaptor.capture());
        List<SyncEvent> events = eventCaptor.getAllValues();

        // 应该有一个target_project_created事件
        boolean hasTargetCreatedEvent = events.stream()
                .anyMatch(event -> "target_project_created".equals(event.getEventType()));
        assertThat(hasTargetCreatedEvent).isTrue();
    }

    /**
     * 测试状态轮询 - 自动创建目标项目失败时记录错误
     */
    @Test
    void testPollMirrorStatus_AutoCreateTargetProjectFailure() {
        // Given: Mirror配置存在
        PushMirrorConfig config = new PushMirrorConfig();
        config.setId(1L);
        config.setSyncProjectId(100L);
        config.setGitlabMirrorId(3001L);
        config.setLastUpdateStatus("failed");
        config.setConsecutiveFailures(1);

        when(pushMirrorConfigMapper.selectBySyncProjectId(100L))
                .thenReturn(config);
        when(sourceProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(sourceProjectInfo);

        // Mock: 目标项目信息存在但状态不是CREATED
        TargetProjectInfo targetInfo = new TargetProjectInfo();
        targetInfo.setId(1L);
        targetInfo.setSyncProjectId(100L);
        targetInfo.setStatus(TargetProjectInfo.Status.NOT_EXIST);
        targetInfo.setPathWithNamespace("group1/project1");

        when(targetProjectInfoMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(targetInfo);

        // Mock: Mirror返回"not found"错误
        remoteMirror.setUpdateStatus("failed");
        remoteMirror.setLastError("remote: The project you were looking for could not be found");
        when(sourceGitLabApiClient.getMirror(1001L, 3001L))
                .thenReturn(remoteMirror);

        // Mock: 自动创建目标项目失败
        doThrow(new RuntimeException("Target GitLab API error"))
                .when(targetProjectManagementService).createTargetProject(100L);

        // When: 轮询状态
        boolean statusChanged = pushMirrorManagementService.pollMirrorStatus(100L);

        // Then: 状态未变化（因为镜像仍然处于failed状态）
        assertThat(statusChanged).isFalse();

        // 验证尝试创建目标项目
        verify(targetProjectManagementService).createTargetProject(100L);

        // 验证不应该触发同步（因为创建失败）
        verify(sourceGitLabApiClient, never()).triggerMirrorSync(anyLong(), anyLong());

        // 验证失败事件被记录
        ArgumentCaptor<SyncEvent> eventCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventMapper, atLeastOnce()).insert(eventCaptor.capture());
        List<SyncEvent> events = eventCaptor.getAllValues();

        // 应该有一个target_project_create_failed事件
        boolean hasFailureEvent = events.stream()
                .anyMatch(event -> "target_project_create_failed".equals(event.getEventType()));
        assertThat(hasFailureEvent).isTrue();
    }

    // Helper methods

    private SourceProjectInfo createSourceProjectInfo(Long syncProjectId, Long gitlabProjectId, String pathWithNamespace) {
        SourceProjectInfo info = new SourceProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(gitlabProjectId);
        info.setPathWithNamespace(pathWithNamespace);
        return info;
    }

    private TargetProjectInfo createTargetProjectInfo(Long syncProjectId, Long gitlabProjectId, String pathWithNamespace) {
        TargetProjectInfo info = new TargetProjectInfo();
        info.setSyncProjectId(syncProjectId);
        info.setGitlabProjectId(gitlabProjectId);
        info.setPathWithNamespace(pathWithNamespace);
        return info;
    }

    private RemoteMirror createRemoteMirror(Long id, String updateStatus) {
        RemoteMirror mirror = new RemoteMirror();
        mirror.setId(id);
        mirror.setEnabled(true);
        mirror.setUpdateStatus(updateStatus);
        mirror.setUrl("http://localhost:9000/test.git");
        mirror.setLastUpdateAt(OffsetDateTime.now());
        return mirror;
    }

    private PushMirrorConfig createMirrorConfig(Long syncProjectId, Long mirrorId, String status) {
        PushMirrorConfig config = new PushMirrorConfig();
        config.setId(syncProjectId);
        config.setSyncProjectId(syncProjectId);
        config.setGitlabMirrorId(mirrorId);
        config.setLastUpdateStatus(status);
        config.setConsecutiveFailures(0);
        return config;
    }
}
