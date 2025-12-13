package com.gitlab.mirror.server.service;

import com.gitlab.mirror.server.entity.PullSyncConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.executor.GitCommandExecutor;
import com.gitlab.mirror.server.mapper.PullSyncConfigMapper;
import com.gitlab.mirror.server.mapper.SourceProjectInfoMapper;
import com.gitlab.mirror.server.mapper.SyncProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Disk Management Service Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DiskManagementServiceTest {

    @Autowired
    private DiskManagementService service;

    @MockBean
    private GitCommandExecutor gitCommandExecutor;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Autowired
    private PullSyncConfigMapper pullSyncConfigMapper;

    @Autowired
    private SourceProjectInfoMapper sourceProjectInfoMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Clean up handled by @Transactional rollback
    }

    @Test
    void testCheckAvailableSpace_Sufficient() {
        // Small requirement, should have space
        boolean hasSpace = service.checkAvailableSpace(1024L, tempDir.toString());

        assertThat(hasSpace).isTrue();
    }

    @Test
    void testCheckAvailableSpace_Insufficient() {
        // Extremely large requirement, should not have space
        boolean hasSpace = service.checkAvailableSpace(Long.MAX_VALUE, tempDir.toString());

        assertThat(hasSpace).isFalse();
    }

    @Test
    void testCheckAvailableSpace_NonExistentPath() throws IOException {
        // Non-existent path should check parent directory
        // Create parent directory first
        Path parentDir = tempDir.resolve("parent");
        Files.createDirectory(parentDir);

        String nonExistentPath = parentDir.toString() + "/nonexistent";
        boolean hasSpace = service.checkAvailableSpace(1024L, nonExistentPath);

        assertThat(hasSpace).isTrue();
    }

    @Test
    void testGetRepositorySize_EmptyDirectory() {
        long size = service.getRepositorySize(tempDir.toString());

        assertThat(size).isEqualTo(0);
    }

    @Test
    void testGetRepositorySize_WithFiles() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");

        Files.writeString(file1, "Hello World");
        Files.writeString(file2, "Test Content");

        long size = service.getRepositorySize(tempDir.toString());

        assertThat(size).isGreaterThan(0);
        assertThat(size).isEqualTo("Hello World".length() + "Test Content".length());
    }

    @Test
    void testGetRepositorySize_NonExistent() {
        long size = service.getRepositorySize("/nonexistent/path");

        assertThat(size).isEqualTo(0);
    }

    @Test
    void testDeleteRepository_Success() throws IOException {
        // Create test directory with files
        Path repoPath = tempDir.resolve("test-repo");
        Files.createDirectory(repoPath);
        Files.writeString(repoPath.resolve("file.txt"), "test");

        // Delete
        boolean deleted = service.deleteRepository(repoPath.toString());

        assertThat(deleted).isTrue();
        assertThat(Files.exists(repoPath)).isFalse();
    }

    @Test
    void testDeleteRepository_NonExistent() {
        // Deleting non-existent path should succeed
        boolean deleted = service.deleteRepository("/nonexistent/path");

        assertThat(deleted).isTrue();
    }

    @Test
    void testCleanupRepository_Success() throws IOException {
        // Create test repository
        Path repoPath = tempDir.resolve("test-repo");
        Files.createDirectory(repoPath);
        Files.writeString(repoPath.resolve("file.txt"), "test data");

        // Mock git gc success
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            true, "SIZE_BEFORE=1000\nSIZE_AFTER=500\n", "", 0
        );
        when(gitCommandExecutor.gcRepository(anyString())).thenReturn(result);

        // Cleanup
        long bytesFreed = service.cleanupRepository(repoPath.toString());

        // Should return non-negative value (actual calculation depends on file sizes)
        assertThat(bytesFreed).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testCleanupRepository_Failure() throws IOException {
        // Create test repository
        Path repoPath = tempDir.resolve("test-repo");
        Files.createDirectory(repoPath);

        // Mock git gc failure
        GitCommandExecutor.GitResult result = new GitCommandExecutor.GitResult(
            false, "", "Git gc failed", 1
        );
        when(gitCommandExecutor.gcRepository(anyString())).thenReturn(result);

        // Cleanup
        long bytesFreed = service.cleanupRepository(repoPath.toString());

        assertThat(bytesFreed).isEqualTo(0);
    }

    @Test
    void testEstimateRequiredSpace_WithRepositorySize() {
        // Create parent sync project first
        SyncProject project = new SyncProject();
        project.setProjectKey("test/project");
        project.setSyncMethod("pull_sync");
        project.setSyncStatus("pending");
        project.setEnabled(true);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        syncProjectMapper.insert(project);

        // Create source project info with repository size
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(project.getId());
        sourceInfo.setGitlabProjectId(100L);
        sourceInfo.setPathWithNamespace("test/project");
        sourceInfo.setName("test-project");
        sourceInfo.setRepositorySize(1000000L); // 1 MB
        sourceInfo.setUpdatedAt(LocalDateTime.now());
        sourceProjectInfoMapper.insert(sourceInfo);

        // Estimate should be 1.2x repository size
        long estimate = service.estimateRequiredSpace(project.getId());

        assertThat(estimate).isEqualTo((long) (1000000 * 1.2));
    }

    @Test
    void testEstimateRequiredSpace_NoRepositorySize() {
        // Create parent sync project first
        SyncProject project = new SyncProject();
        project.setProjectKey("test/project2");
        project.setSyncMethod("pull_sync");
        project.setSyncStatus("pending");
        project.setEnabled(true);
        project.setCreatedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        syncProjectMapper.insert(project);

        // Create source project info without repository size
        SourceProjectInfo sourceInfo = new SourceProjectInfo();
        sourceInfo.setSyncProjectId(project.getId());
        sourceInfo.setGitlabProjectId(101L);
        sourceInfo.setPathWithNamespace("test/project2");
        sourceInfo.setName("test-project2");
        sourceInfo.setUpdatedAt(LocalDateTime.now());
        sourceProjectInfoMapper.insert(sourceInfo);

        // Should return default estimate (100 MB)
        long estimate = service.estimateRequiredSpace(project.getId());

        assertThat(estimate).isEqualTo(100L * 1024 * 1024);
    }

    @Test
    void testEstimateRequiredSpace_ProjectNotFound() {
        // Should return default estimate (100 MB)
        long estimate = service.estimateRequiredSpace(999L);

        assertThat(estimate).isEqualTo(100L * 1024 * 1024);
    }

    @Test
    void testCalculateDiskUsage() {
        // This test requires actual file system operations, so we just verify it doesn't crash
        DiskManagementService.DiskUsageStats stats = service.calculateDiskUsage();

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalRepositories()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getTotalSizeBytes()).isGreaterThanOrEqualTo(0);
    }
}
