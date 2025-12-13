package com.gitlab.mirror.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pull Sync Config Service
 * <p>
 * Manages Pull sync configuration for projects
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PullSyncConfigService {

    private final PullSyncConfigMapper pullSyncConfigMapper;

    /**
     * Initialize Pull sync configuration for a project
     *
     * @param syncProjectId Sync project ID
     * @param projectKey    Project key (path)
     * @return Created configuration
     */
    @Transactional
    public PullSyncConfig initializeConfig(Long syncProjectId, String projectKey) {
        log.info("Initializing Pull sync config for project: {}", projectKey);

        // Check if config already exists
        LambdaQueryWrapper<PullSyncConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PullSyncConfig::getSyncProjectId, syncProjectId);
        PullSyncConfig existing = pullSyncConfigMapper.selectOne(queryWrapper);

        if (existing != null) {
            log.debug("Pull sync config already exists for project: {}, id={}", projectKey, existing.getId());
            return existing;
        }

        // Create new config with defaults
        PullSyncConfig config = new PullSyncConfig();
        config.setSyncProjectId(syncProjectId);
        config.setPriority(PullSyncConfig.Priority.NORMAL);
        config.setEnabled(true);
        config.setLocalRepoPath(generateLocalRepoPath(projectKey));

        pullSyncConfigMapper.insert(config);

        log.info("Created Pull sync config for project: {}, id={}, priority={}, path={}",
                projectKey, config.getId(), config.getPriority(), config.getLocalRepoPath());

        return config;
    }

    /**
     * Update priority for a project
     *
     * @param syncProjectId Sync project ID
     * @param priority      New priority
     */
    @Transactional
    public void updatePriority(Long syncProjectId, String priority) {
        LambdaQueryWrapper<PullSyncConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PullSyncConfig::getSyncProjectId, syncProjectId);
        PullSyncConfig config = pullSyncConfigMapper.selectOne(queryWrapper);

        if (config == null) {
            throw new IllegalArgumentException("Pull sync config not found for syncProjectId: " + syncProjectId);
        }

        config.setPriority(priority);
        pullSyncConfigMapper.updateById(config);

        log.info("Updated priority for syncProjectId={}: {}", syncProjectId, priority);
    }

    /**
     * Set enabled status for a project
     *
     * @param syncProjectId Sync project ID
     * @param enabled       Enabled status
     */
    @Transactional
    public void setEnabled(Long syncProjectId, boolean enabled) {
        LambdaQueryWrapper<PullSyncConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PullSyncConfig::getSyncProjectId, syncProjectId);
        PullSyncConfig config = pullSyncConfigMapper.selectOne(queryWrapper);

        if (config == null) {
            throw new IllegalArgumentException("Pull sync config not found for syncProjectId: " + syncProjectId);
        }

        config.setEnabled(enabled);
        pullSyncConfigMapper.updateById(config);

        log.info("Set enabled={} for syncProjectId={}", enabled, syncProjectId);
    }

    /**
     * Get configuration by sync project ID
     *
     * @param syncProjectId Sync project ID
     * @return Configuration or null if not found
     */
    public PullSyncConfig getConfig(Long syncProjectId) {
        LambdaQueryWrapper<PullSyncConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PullSyncConfig::getSyncProjectId, syncProjectId);
        return pullSyncConfigMapper.selectOne(queryWrapper);
    }

    /**
     * Generate local repository path based on project key
     * Format: {base-path}/{project-key}
     *
     * @param projectKey Project key (e.g., "group1/project-a")
     * @return Local repository path
     */
    private String generateLocalRepoPath(String projectKey) {
        // Base path will be configured in application properties
        // For now, use a default path
        String basePath = System.getProperty("user.home") + "/.gitlab-mirror/repos";
        return basePath + "/" + projectKey;
    }
}
