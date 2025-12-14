package com.gitlab.mirror.server.service.monitor;

import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.common.model.RepositoryBranch;
import com.gitlab.mirror.server.client.RetryableGitLabClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Batch Query Executor
 * <p>
 * Provides batch query functionality for GitLab projects with pagination support.
 * Supports incremental queries using updated_after parameter and repository statistics.
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
public class BatchQueryExecutor {

    private static final int DEFAULT_PER_PAGE = 50;
    private static final int MAX_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;
    private static final int CONCURRENT_QUERIES = 5;  // Max concurrent queries
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private final RetryableGitLabClient sourceClient;
    private final RetryableGitLabClient targetClient;
    private final ExecutorService executorService;

    public BatchQueryExecutor(
            @Qualifier("sourceGitLabClient") RetryableGitLabClient sourceClient,
            @Qualifier("targetGitLabClient") RetryableGitLabClient targetClient) {
        this.sourceClient = sourceClient;
        this.targetClient = targetClient;
        this.executorService = Executors.newFixedThreadPool(CONCURRENT_QUERIES);
    }

    /**
     * Query projects from source GitLab
     *
     * @param updatedAfter Filter projects updated after this time (optional, for incremental queries)
     * @param perPage Number of results per page (default: 50)
     * @return List of projects with statistics
     */
    public List<GitLabProject> querySourceProjects(LocalDateTime updatedAfter, Integer perPage) {
        log.info("Querying source projects - updatedAfter: {}, perPage: {}", updatedAfter, perPage);
        return queryProjects(sourceClient, updatedAfter, perPage);
    }

    /**
     * Query projects from target GitLab
     *
     * @param updatedAfter Filter projects updated after this time (optional, for incremental queries)
     * @param perPage Number of results per page (default: 50)
     * @return List of projects with statistics
     */
    public List<GitLabProject> queryTargetProjects(LocalDateTime updatedAfter, Integer perPage) {
        log.info("Querying target projects - updatedAfter: {}, perPage: {}", updatedAfter, perPage);
        return queryProjects(targetClient, updatedAfter, perPage);
    }

    /**
     * Query projects with pagination and optional incremental filter
     *
     * @param client GitLab API client
     * @param updatedAfter Filter projects updated after this time (optional)
     * @param perPage Number of results per page
     * @return List of all projects matching criteria
     */
    private List<GitLabProject> queryProjects(RetryableGitLabClient client, LocalDateTime updatedAfter, Integer perPage) {
        List<GitLabProject> allProjects = new ArrayList<>();
        int page = 1;
        int pageSize = perPage != null ? perPage : DEFAULT_PER_PAGE;

        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                // Build query URL with parameters
                UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v4/projects")
                        .queryParam("page", page)
                        .queryParam("per_page", pageSize)
                        .queryParam("statistics", true)  // Get repository statistics (size, etc.)
                        .queryParam("with_custom_attributes", false)  // Performance optimization
                        .queryParam("membership", true);  // Only projects user has access to

                // Add incremental query parameter if specified
                if (updatedAfter != null) {
                    String updatedAfterStr = updatedAfter.format(ISO_FORMATTER);
                    builder.queryParam("updated_after", updatedAfterStr);
                    builder.queryParam("order_by", "updated_at");  // Required when using updated_after
                }

                String path = builder.build().toUriString();

                log.debug("Querying page {} with pageSize {} (updatedAfter: {})", page, pageSize, updatedAfter);

                // Execute GET request with timeout and retry
                GitLabProject[] projects = executeWithRetry(() -> client.get(path, GitLabProject[].class));

                if (projects == null || projects.length == 0) {
                    log.debug("No more projects found (page: {})", page);
                    break;
                }

                allProjects.addAll(List.of(projects));
                log.debug("Fetched {} projects from page {}, total so far: {}", projects.length, page, allProjects.size());

                // Check if this is the last page
                if (projects.length < pageSize) {
                    log.debug("Last page reached (projects: {} < pageSize: {})", projects.length, pageSize);
                    break;
                }

                // Check timeout
                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsedSeconds > MAX_TIMEOUT_SECONDS) {
                    log.warn("Query timeout reached ({} seconds), stopping at page {}", elapsedSeconds, page);
                    break;
                }

                page++;

            } catch (Exception e) {
                log.error("Error querying projects at page {}: {}", page, e.getMessage(), e);
                throw new RuntimeException("Failed to query projects at page " + page, e);
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        log.info("Batch query completed - total projects: {}, pages: {}, time: {}ms",
                allProjects.size(), page, elapsedTime);

        return allProjects;
    }

    /**
     * Execute API call with retry logic
     *
     * @param operation The API operation to execute
     * @param <T> Return type
     * @return Result from the operation
     */
    private <T> T executeWithRetry(Operation<T> operation) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    long waitMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 2s, 4s, 8s
                    log.warn("API call failed (attempt {}/{}), retrying after {}ms: {}",
                            attempt, MAX_RETRIES, waitMs, e.getMessage());
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        throw new RuntimeException("API call failed after " + MAX_RETRIES + " retries", lastException);
    }

    /**
     * Get detailed project information including branches and commits
     *
     * @param projectId GitLab project ID
     * @param client GitLab API client (source or target)
     * @return Project details with branch count, commit count, and latest commit SHA
     */
    public ProjectDetails getProjectDetails(Long projectId, RetryableGitLabClient client) {
        log.debug("Getting details for project {}", projectId);

        ProjectDetails details = new ProjectDetails();
        details.setProjectId(projectId);

        try {
            // Query branches and latest commit sequentially
            // Note: We don't use nested CompletableFuture here to avoid thread pool deadlock
            // The outer getProjectDetailsBatch() already provides concurrency
            try {
                RepositoryBranch[] branches = executeWithRetry(() -> getBranches(projectId, client));
                details.setBranchCount(branches != null ? branches.length : 0);
            } catch (Exception e) {
                // Some projects may not have branches (empty repos), log warning and continue
                log.warn("Failed to get branches for project {}: {}", projectId, e.getMessage());
                details.setBranchCount(0);
            }

            try {
                String latestCommitSha = executeWithRetry(() -> getLatestCommitSha(projectId, client));
                details.setLatestCommitSha(latestCommitSha);
            } catch (Exception e) {
                // Some projects may not have commits (empty repos), log warning and continue
                log.warn("Failed to get latest commit for project {}: {}", projectId, e.getMessage());
                details.setLatestCommitSha(null);
            }

            // For commit count, we can optionally query commits API
            // But this is expensive, so we skip it for now
            // details.setCommitCount(getCommitCount(projectId, client));

            log.debug("Project {} details: branches={}, latestCommitSha={}",
                    projectId, details.getBranchCount(), details.getLatestCommitSha());

        } catch (Exception e) {
            log.error("Failed to get project details for {}: {}", projectId, e.getMessage(), e);
            // Return details with default values instead of throwing
            return details;
        }

        return details;
    }

    /**
     * Get detailed information for multiple projects concurrently
     *
     * @param projectIds List of project IDs
     * @param client GitLab API client
     * @return List of project details
     */
    public List<ProjectDetails> getProjectDetailsBatch(List<Long> projectIds, RetryableGitLabClient client) {
        log.info("Getting details for {} projects concurrently", projectIds.size());

        List<CompletableFuture<ProjectDetails>> futures = projectIds.stream()
                .map(projectId -> CompletableFuture.supplyAsync(
                        () -> getProjectDetails(projectId, client),
                        executorService
                ))
                .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to get batch project details: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get batch project details", e);
        }
    }

    /**
     * Get branches for a project
     */
    private RepositoryBranch[] getBranches(Long projectId, RetryableGitLabClient client) throws Exception {
        String path = UriComponentsBuilder.fromPath("/api/v4/projects/{id}/repository/branches")
                .buildAndExpand(projectId)
                .toUriString();

        return client.get(path, RepositoryBranch[].class);
    }

    /**
     * Get latest commit SHA from default branch
     */
    private String getLatestCommitSha(Long projectId, RetryableGitLabClient client) throws Exception {
        // First get project to find default branch
        String projectPath = "/api/v4/projects/" + projectId;
        GitLabProject project = client.get(projectPath, GitLabProject.class);

        if (project == null || project.getDefaultBranch() == null) {
            return null;
        }

        // Get branch details for default branch
        String branchPath = UriComponentsBuilder
                .fromPath("/api/v4/projects/{id}/repository/branches/{branch}")
                .buildAndExpand(projectId, project.getDefaultBranch())
                .toUriString();

        RepositoryBranch branch = client.get(branchPath, RepositoryBranch.class);
        return branch != null && branch.getCommit() != null ? branch.getCommit().getId() : null;
    }

    /**
     * Get source GitLab client
     */
    public RetryableGitLabClient getSourceClient() {
        return sourceClient;
    }

    /**
     * Get target GitLab client
     */
    public RetryableGitLabClient getTargetClient() {
        return targetClient;
    }

    /**
     * Functional interface for retry operations
     */
    @FunctionalInterface
    private interface Operation<T> {
        T execute() throws Exception;
    }

    /**
     * Project details DTO
     */
    @Data
    public static class ProjectDetails {
        private Long projectId;
        private String latestCommitSha;
        private Integer commitCount;
        private Integer branchCount;
    }
}
