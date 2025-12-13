package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Disk Management Service
 * <p>
 * Manages local repository storage, cleanup, and disk usage statistics
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiskManagementService {

    private final GitCommandExecutor gitCommandExecutor;
    private final PullSyncConfigMapper pullSyncConfigMapper;
    private final SourceProjectInfoMapper sourceProjectInfoMapper;

    /**
     * Disk usage statistics
     */
    public static class DiskUsageStats {
        private long totalRepositories;
        private long totalSizeBytes;
        private long availableSpaceBytes;
        private long usedSpaceBytes;
        private Map<String, Long> repositorySizes;

        public DiskUsageStats() {
            this.repositorySizes = new HashMap<>();
        }

        public long getTotalRepositories() {
            return totalRepositories;
        }

        public void setTotalRepositories(long totalRepositories) {
            this.totalRepositories = totalRepositories;
        }

        public long getTotalSizeBytes() {
            return totalSizeBytes;
        }

        public void setTotalSizeBytes(long totalSizeBytes) {
            this.totalSizeBytes = totalSizeBytes;
        }

        public long getAvailableSpaceBytes() {
            return availableSpaceBytes;
        }

        public void setAvailableSpaceBytes(long availableSpaceBytes) {
            this.availableSpaceBytes = availableSpaceBytes;
        }

        public long getUsedSpaceBytes() {
            return usedSpaceBytes;
        }

        public void setUsedSpaceBytes(long usedSpaceBytes) {
            this.usedSpaceBytes = usedSpaceBytes;
        }

        public Map<String, Long> getRepositorySizes() {
            return repositorySizes;
        }

        public void setRepositorySizes(Map<String, Long> repositorySizes) {
            this.repositorySizes = repositorySizes;
        }
    }

    /**
     * Check if required disk space is available
     *
     * @param requiredBytes Required space in bytes
     * @return true if space is available
     */
    public boolean checkAvailableSpace(long requiredBytes) {
        return checkAvailableSpace(requiredBytes, getDefaultBasePath());
    }

    /**
     * Check if required disk space is available at specific path
     *
     * @param requiredBytes Required space in bytes
     * @param path          Path to check
     * @return true if space is available
     */
    public boolean checkAvailableSpace(long requiredBytes, String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                file = file.getParentFile();
                if (file == null) {
                    return false;
                }
            }

            long availableSpace = file.getUsableSpace();
            boolean hasSpace = availableSpace >= requiredBytes;

            if (!hasSpace) {
                log.warn("Insufficient disk space at {}: required={} bytes, available={} bytes",
                    path, requiredBytes, availableSpace);
            }

            return hasSpace;
        } catch (Exception e) {
            log.error("Failed to check disk space at {}", path, e);
            return false;
        }
    }

    /**
     * Cleanup repository using git gc
     *
     * @param localPath Local repository path
     * @return Bytes freed (estimated)
     */
    public long cleanupRepository(String localPath) {
        log.info("Cleaning up repository: {}", localPath);

        try {
            // Get size before cleanup
            long sizeBefore = getRepositorySize(localPath);

            // Execute git gc
            GitCommandExecutor.GitResult result = gitCommandExecutor.gcRepository(localPath);

            if (!result.isSuccess()) {
                log.warn("Git gc failed for {}: {}", localPath, result.getError());
                return 0;
            }

            // Get size after cleanup
            long sizeAfter = getRepositorySize(localPath);
            long bytesFreed = sizeBefore - sizeAfter;

            log.info("Repository cleanup completed: {}, freed {} bytes", localPath, bytesFreed);
            return Math.max(0, bytesFreed);

        } catch (Exception e) {
            log.error("Failed to cleanup repository: {}", localPath, e);
            return 0;
        }
    }

    /**
     * Delete repository and its directory
     *
     * @param localPath Local repository path
     * @return true if deletion successful
     */
    public boolean deleteRepository(String localPath) {
        log.info("Deleting repository: {}", localPath);

        try {
            Path path = Paths.get(localPath);
            if (!Files.exists(path)) {
                log.warn("Repository path does not exist: {}", localPath);
                return true;
            }

            // Delete directory recursively
            deleteDirectoryRecursively(path);

            log.info("Repository deleted successfully: {}", localPath);
            return true;

        } catch (Exception e) {
            log.error("Failed to delete repository: {}", localPath, e);
            return false;
        }
    }

    /**
     * Calculate disk usage statistics
     *
     * @return Disk usage statistics
     */
    public DiskUsageStats calculateDiskUsage() {
        log.info("Calculating disk usage statistics");

        DiskUsageStats stats = new DiskUsageStats();

        try {
            // Get all pull sync configs with local repo paths
            List<PullSyncConfig> configs = pullSyncConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PullSyncConfig>()
                    .isNotNull("local_repo_path")
            );

            long totalSize = 0;
            for (PullSyncConfig config : configs) {
                String localPath = config.getLocalRepoPath();
                if (localPath != null && !localPath.isEmpty()) {
                    long size = getRepositorySize(localPath);
                    stats.getRepositorySizes().put(localPath, size);
                    totalSize += size;
                }
            }

            stats.setTotalRepositories(configs.size());
            stats.setTotalSizeBytes(totalSize);

            // Get available and used space
            String basePath = getDefaultBasePath();
            File baseDir = new File(basePath);
            if (baseDir.exists()) {
                stats.setAvailableSpaceBytes(baseDir.getUsableSpace());
                stats.setUsedSpaceBytes(baseDir.getTotalSpace() - baseDir.getFreeSpace());
            }

            log.info("Disk usage: {} repositories, {} bytes total",
                stats.getTotalRepositories(), stats.getTotalSizeBytes());

            return stats;

        } catch (Exception e) {
            log.error("Failed to calculate disk usage", e);
            return stats;
        }
    }

    /**
     * Get repository size in bytes
     *
     * @param localPath Local repository path
     * @return Size in bytes
     */
    public long getRepositorySize(String localPath) {
        try {
            Path path = Paths.get(localPath);
            if (!Files.exists(path)) {
                return 0;
            }

            AtomicLong size = new AtomicLong(0);
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Skip files we can't access
                    return FileVisitResult.CONTINUE;
                }
            });

            return size.get();

        } catch (Exception e) {
            log.error("Failed to calculate repository size: {}", localPath, e);
            return 0;
        }
    }

    /**
     * Estimate required disk space for repository
     *
     * @param syncProjectId Sync project ID
     * @return Estimated space in bytes
     */
    public long estimateRequiredSpace(Long syncProjectId) {
        try {
            SourceProjectInfo sourceInfo = sourceProjectInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SourceProjectInfo>()
                    .eq("sync_project_id", syncProjectId)
            );

            if (sourceInfo != null && sourceInfo.getRepositorySize() != null) {
                // Add 20% buffer for .git metadata
                return (long) (sourceInfo.getRepositorySize() * 1.2);
            }

            // Default estimate: 100 MB
            return 100L * 1024 * 1024;

        } catch (Exception e) {
            log.error("Failed to estimate required space for project: {}", syncProjectId, e);
            return 100L * 1024 * 1024;
        }
    }

    /**
     * Get default base path for local repositories
     *
     * @return Base path
     */
    private String getDefaultBasePath() {
        String homeDir = System.getProperty("user.home");
        return homeDir + "/.gitlab-sync/repos";
    }

    /**
     * Delete directory recursively
     *
     * @param path Directory path
     * @throws IOException if deletion fails
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
