package com.gitlab.mirror.server.client;

import com.gitlab.mirror.common.model.GitLabGroup;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.common.model.RemoteMirror;
import com.gitlab.mirror.common.model.RepositoryBranch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitLab API Client
 * <p>
 * Provides high-level GitLab API operations for projects, groups, and mirrors
 *
 * @author GitLab Mirror Team
 */
@Slf4j
public class GitLabApiClient {

    private final RetryableGitLabClient client;

    public GitLabApiClient(RetryableGitLabClient client) {
        this.client = client;
    }

    // ==================== Project Discovery API ====================

    /**
     * Get all groups (optionally recursive)
     */
    public List<GitLabGroup> getGroups(String parentPath, boolean recursive) {
        List<GitLabGroup> allGroups = new ArrayList<>();
        int page = 1;
        int perPage = 100;

        while (true) {
            String path = UriComponentsBuilder.fromPath("/api/v4/groups")
                    .queryParam("page", page)
                    .queryParam("per_page", perPage)
                    .queryParam("all_available", true)
                    .build()
                    .toUriString();

            GitLabGroup[] groups = client.get(path, GitLabGroup[].class);
            if (groups == null || groups.length == 0) {
                break;
            }

            // Filter by parent path if specified
            for (GitLabGroup group : groups) {
                if (parentPath == null || group.getFullPath().startsWith(parentPath + "/")) {
                    allGroups.add(group);
                }
            }

            if (groups.length < perPage) {
                break; // Last page
            }
            page++;
        }

        log.info("Found {} groups", allGroups.size());
        return allGroups;
    }

    /**
     * Get projects in a group (with pagination)
     */
    public List<GitLabProject> getProjects(String groupPath, int page, int pageSize) {
        List<GitLabProject> projects = new ArrayList<>();

        if (groupPath != null && !groupPath.isEmpty()) {
            // Get projects in specific group
            String encodedPath = URLEncoder.encode(groupPath, StandardCharsets.UTF_8);
            String path = UriComponentsBuilder.fromPath("/api/v4/groups/" + encodedPath + "/projects")
                    .queryParam("page", page)
                    .queryParam("per_page", pageSize)
                    .queryParam("include_subgroups", true)
                    .build()
                    .toUriString();

            GitLabProject[] result = client.get(path, GitLabProject[].class);
            if (result != null) {
                projects.addAll(List.of(result));
            }
        } else {
            // Get all projects
            String path = UriComponentsBuilder.fromPath("/api/v4/projects")
                    .queryParam("page", page)
                    .queryParam("per_page", pageSize)
                    .queryParam("membership", true)
                    .build()
                    .toUriString();

            GitLabProject[] result = client.get(path, GitLabProject[].class);
            if (result != null) {
                projects.addAll(List.of(result));
            }
        }

        return projects;
    }

    /**
     * Get all projects in a group (auto-pagination)
     */
    public List<GitLabProject> getAllProjects(String groupPath) {
        List<GitLabProject> allProjects = new ArrayList<>();
        int page = 1;
        int pageSize = 100;

        while (true) {
            List<GitLabProject> projects = getProjects(groupPath, page, pageSize);
            if (projects.isEmpty()) {
                break;
            }
            allProjects.addAll(projects);

            if (projects.size() < pageSize) {
                break; // Last page
            }
            page++;
        }

        log.info("Found {} projects in group '{}'", allProjects.size(), groupPath);
        return allProjects;
    }

    /**
     * Get project details by path
     */
    public GitLabProject getProject(String projectPath) {
        String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
        String path = "/api/v4/projects/" + encodedPath;
        return client.get(path, GitLabProject.class);
    }

    // ==================== Group/Project Management API ====================

    /**
     * Check if group exists
     */
    public boolean groupExists(String groupPath) {
        try {
            String encodedPath = URLEncoder.encode(groupPath, StandardCharsets.UTF_8);
            String path = "/api/v4/groups/" + encodedPath;
            client.get(path, GitLabGroup.class);
            return true;
        } catch (GitLabClientException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Check if project exists
     */
    public boolean projectExists(String projectPath) {
        try {
            getProject(projectPath);
            return true;
        } catch (GitLabClientException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Create group
     */
    public GitLabGroup createGroup(String path, String name, String parentPath) {
        Map<String, Object> body = new HashMap<>();
        body.put("path", path);
        body.put("name", name);
        body.put("visibility", "private");

        if (parentPath != null && !parentPath.isEmpty()) {
            // Find parent group ID
            String encodedParentPath = URLEncoder.encode(parentPath, StandardCharsets.UTF_8);
            GitLabGroup parentGroup = client.get("/api/v4/groups/" + encodedParentPath, GitLabGroup.class);
            body.put("parent_id", parentGroup.getId());
        }

        log.info("Creating group: {}", path);
        return client.post("/api/v4/groups", body, GitLabGroup.class);
    }

    /**
     * Create project
     */
    public GitLabProject createProject(String path, String name, String groupPath) {
        Map<String, Object> body = new HashMap<>();
        body.put("path", path);
        body.put("name", name);
        body.put("visibility", "private");

        if (groupPath != null && !groupPath.isEmpty()) {
            // Get namespace ID
            String encodedGroupPath = URLEncoder.encode(groupPath, StandardCharsets.UTF_8);
            GitLabGroup group = client.get("/api/v4/groups/" + encodedGroupPath, GitLabGroup.class);
            body.put("namespace_id", group.getId());
        }

        log.info("Creating project: {}/{}", groupPath, path);
        return client.post("/api/v4/projects", body, GitLabProject.class);
    }

    // ==================== Push Mirror Management API ====================

    /**
     * Create remote mirror
     */
    public RemoteMirror createMirror(Long projectId, String mirrorUrl, boolean onlyProtectedBranches) {
        Map<String, Object> body = new HashMap<>();
        body.put("url", mirrorUrl);
        body.put("enabled", true);
        body.put("only_protected_branches", onlyProtectedBranches);

        log.info("Creating mirror for project {} to {}", projectId, mirrorUrl);
        return client.post("/api/v4/projects/" + projectId + "/remote_mirrors", body, RemoteMirror.class);
    }

    /**
     * Get all remote mirrors for a project
     */
    public List<RemoteMirror> getMirrors(Long projectId) {
        String path = "/api/v4/projects/" + projectId + "/remote_mirrors";
        RemoteMirror[] mirrors = client.get(path, RemoteMirror[].class);
        return mirrors != null ? List.of(mirrors) : new ArrayList<>();
    }

    /**
     * Get remote mirror by ID
     */
    public RemoteMirror getMirror(Long projectId, Long mirrorId) {
        String path = "/api/v4/projects/" + projectId + "/remote_mirrors/" + mirrorId;
        return client.get(path, RemoteMirror.class);
    }

    /**
     * Trigger mirror sync
     */
    public void triggerMirrorSync(Long projectId, Long mirrorId) {
        log.info("Triggering mirror sync for project {} mirror {}", projectId, mirrorId);
        client.post("/api/v4/projects/" + projectId + "/mirror/pull", null, Object.class);
    }

    /**
     * Delete remote mirror
     */
    public void deleteMirror(Long projectId, Long mirrorId) {
        log.info("Deleting mirror {} from project {}", mirrorId, projectId);
        client.delete("/api/v4/projects/" + projectId + "/remote_mirrors/" + mirrorId);
    }

    // ==================== Repository Consistency Check API ====================

    /**
     * Get repository branches
     */
    public List<RepositoryBranch> getBranches(Long projectId) {
        String path = "/api/v4/projects/" + projectId + "/repository/branches";
        RepositoryBranch[] branches = client.get(path, RepositoryBranch[].class);
        return branches != null ? List.of(branches) : new ArrayList<>();
    }

    /**
     * Get default branch
     */
    public RepositoryBranch getDefaultBranch(String projectPath) {
        GitLabProject project = getProject(projectPath);
        if (project.getDefaultBranch() == null) {
            return null;
        }

        String path = "/api/v4/projects/" + project.getId() + "/repository/branches/" + project.getDefaultBranch();
        return client.get(path, RepositoryBranch.class);
    }

    /**
     * Test connection
     */
    public boolean testConnection() {
        return client.testConnection();
    }
}
