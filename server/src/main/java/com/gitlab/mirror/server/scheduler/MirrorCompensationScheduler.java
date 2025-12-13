package com.gitlab.mirror.server.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gitlab.mirror.server.config.properties.GitLabMirrorProperties;
import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import com.gitlab.mirror.server.mapper.PushMirrorConfigMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import com.gitlab.mirror.server.mapper.TargetProjectInfoMapper;
import com.gitlab.mirror.server.service.PushMirrorManagementService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirror Compensation Scheduler
 * <p>
 * 定时检测和补偿创建未配置 Mirror 的同步项目
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MirrorCompensationScheduler {

    private final SyncProjectMapper syncProjectMapper;
    private final TargetProjectInfoMapper targetProjectInfoMapper;
    private final PushMirrorConfigMapper pushMirrorConfigMapper;
    private final PushMirrorManagementService pushMirrorManagementService;
    private final GitLabMirrorProperties properties;

    /**
     * 应用启动完成后立即执行一次补偿检查
     * <p>
     * 使用 ApplicationReadyEvent 确保所有 Bean 初始化完成且数据库连接已建立
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready, triggering initial mirror compensation check...");
        compensateMirrorConfiguration();
    }

    /**
     * 定时检测并补偿创建 Mirror 配置
     * <p>
     * 检测条件:
     * 1. sync_status = 'target_created' (目标项目已创建但 Mirror 未配置)
     * 2. enabled = true (同步已启用)
     * 3. 存在 target_project_info 记录
     * 4. 不存在 push_mirror_config 记录或 gitlab_mirror_id 为 NULL
     * <p>
     * 默认每5分钟执行一次，可通过 gitlab.mirror.sync.compensation-interval 配置
     */
    @Scheduled(fixedDelayString = "${gitlab.mirror.sync.compensation-interval:300000}")
    public void compensateMirrorConfiguration() {
        if (!properties.getSync().getEnabled()) {
            log.debug("Sync is disabled, skipping mirror compensation");
            return;
        }

        log.info("Starting mirror compensation scheduler...");

        executeCompensation();
    }

    /**
     * 执行补偿逻辑
     */
    private void executeCompensation() {
        try {
            // 1. 查询需要补偿的同步项目
            List<SyncProject> projectsNeedCompensation = findProjectsNeedingCompensation();

            if (projectsNeedCompensation.isEmpty()) {
                log.info("No projects need mirror compensation");
                return;
            }

            log.info("Found {} projects needing mirror compensation", projectsNeedCompensation.size());

            // 2. 批量创建 Mirror 配置
            int successCount = 0;
            int failureCount = 0;

            for (SyncProject syncProject : projectsNeedCompensation) {
                try {
                    log.info("Compensating mirror for project: {} (id={})",
                            syncProject.getProjectKey(), syncProject.getId());

                    // 调用 Mirror 配置服务
                    pushMirrorManagementService.configureMirror(syncProject.getId());

                    // 更新同步项目状态为 mirror_configured
                    syncProject.setSyncStatus(SyncProject.SyncStatus.MIRROR_CONFIGURED);
                    syncProject.setErrorMessage(null);
                    syncProjectMapper.updateById(syncProject);

                    successCount++;
                    log.info("Successfully compensated mirror for project: {} (id={})",
                            syncProject.getProjectKey(), syncProject.getId());

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to compensate mirror for project: {} (id={})",
                            syncProject.getProjectKey(), syncProject.getId(), e);

                    // 更新同步项目状态为 failed
                    syncProject.setSyncStatus(SyncProject.SyncStatus.FAILED);
                    syncProject.setErrorMessage("Mirror compensation failed: " + e.getMessage());
                    syncProjectMapper.updateById(syncProject);
                }
            }

            log.info("Mirror compensation completed: {} succeeded, {} failed, {} total",
                    successCount, failureCount, projectsNeedCompensation.size());

        } catch (Exception e) {
            log.error("Mirror compensation scheduler failed", e);
        }
    }

    /**
     * 查询需要补偿 Mirror 配置的项目
     * <p>
     * 查询条件:
     * 1. sync_status = 'target_created'
     * 2. enabled = true
     * 3. 存在 target_project_info 记录
     * 4. 不存在 push_mirror_config 或 gitlab_mirror_id 为 NULL
     */
    private List<SyncProject> findProjectsNeedingCompensation() {
        // 查询状态为 target_created 且已启用的同步项目
        QueryWrapper<SyncProject> wrapper = new QueryWrapper<>();
        wrapper.eq("sync_status", SyncProject.SyncStatus.TARGET_CREATED)
                .eq("enabled", true);

        List<SyncProject> candidates = syncProjectMapper.selectList(wrapper);
        List<SyncProject> result = new ArrayList<>();

        for (SyncProject syncProject : candidates) {
            // 检查是否存在目标项目信息
            QueryWrapper<TargetProjectInfo> targetWrapper = new QueryWrapper<>();
            targetWrapper.eq("sync_project_id", syncProject.getId());
            TargetProjectInfo targetInfo = targetProjectInfoMapper.selectOne(targetWrapper);

            if (targetInfo == null) {
                log.debug("Skipping project {} (id={}): target_project_info not found",
                        syncProject.getProjectKey(), syncProject.getId());
                continue;
            }

            // 检查 Mirror 配置是否存在或未完成
            PushMirrorConfig mirrorConfig = pushMirrorConfigMapper.selectBySyncProjectId(syncProject.getId());

            if (mirrorConfig == null || mirrorConfig.getGitlabMirrorId() == null) {
                result.add(syncProject);
                log.debug("Project {} (id={}) needs mirror compensation",
                        syncProject.getProjectKey(), syncProject.getId());
            }
        }

        return result;
    }
}
