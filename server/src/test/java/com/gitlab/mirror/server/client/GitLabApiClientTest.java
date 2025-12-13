package com.gitlab.mirror.server.client;

import com.gitlab.mirror.common.model.GitLabGroup;
import com.gitlab.mirror.common.model.GitLabProject;
import com.gitlab.mirror.common.model.RemoteMirror;
import com.gitlab.mirror.common.model.RepositoryBranch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GitLab API Client Test
 *
 * @author GitLab Mirror Team
 */
class GitLabApiClientTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String TOKEN = "test-token";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private GitLabApiClient apiClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        RetryableGitLabClient client = new RetryableGitLabClient(restTemplate, BASE_URL, TOKEN, 3, 100);
        apiClient = new GitLabApiClient(client);
    }

    @Test
    void testGetGroups() {
        // Setup
        String groupsJson = "[{\"id\":1,\"name\":\"Group1\",\"path\":\"group1\",\"full_path\":\"group1\"}," +
                "{\"id\":2,\"name\":\"Group2\",\"path\":\"group2\",\"full_path\":\"group2\"}]";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/groups?page=1&per_page=100&all_available=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(groupsJson, MediaType.APPLICATION_JSON));

        // Execute
        List<GitLabGroup> groups = apiClient.getGroups(null, false);

        // Verify
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).getName()).isEqualTo("Group1");
        assertThat(groups.get(1).getName()).isEqualTo("Group2");
        mockServer.verify();
    }

    @Test
    void testGetAllProjects() {
        // Setup - page 1
        String projectsJson1 = "[{\"id\":1,\"name\":\"Project1\",\"path\":\"project1\",\"path_with_namespace\":\"group1/project1\"}]";
        mockServer.expect(requestTo(BASE_URL + "/api/v4/groups/group1/projects?page=1&per_page=100&include_subgroups=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(projectsJson1, MediaType.APPLICATION_JSON));

        // Execute
        List<GitLabProject> projects = apiClient.getAllProjects("group1");

        // Verify
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getName()).isEqualTo("Project1");
        mockServer.verify();
    }

    @Test
    void testGetProject() {
        // Setup
        String projectJson = "{\"id\":123,\"name\":\"TestProject\",\"path\":\"test-project\",\"path_with_namespace\":\"group1/test-project\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/group1%252Ftest-project"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(projectJson, MediaType.APPLICATION_JSON));

        // Execute
        GitLabProject project = apiClient.getProject("group1/test-project");

        // Verify
        assertThat(project).isNotNull();
        assertThat(project.getId()).isEqualTo(123L);
        assertThat(project.getName()).isEqualTo("TestProject");
        mockServer.verify();
    }

    @Test
    void testGroupExists() {
        // Setup
        String groupJson = "{\"id\":1,\"name\":\"Group1\",\"path\":\"group1\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/groups/group1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(groupJson, MediaType.APPLICATION_JSON));

        // Execute
        boolean exists = apiClient.groupExists("group1");

        // Verify
        assertThat(exists).isTrue();
        mockServer.verify();
    }

    @Test
    void testProjectExists() {
        // Setup
        String projectJson = "{\"id\":123,\"name\":\"Project1\",\"path\":\"project1\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/group1%252Fproject1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(projectJson, MediaType.APPLICATION_JSON));

        // Execute
        boolean exists = apiClient.projectExists("group1/project1");

        // Verify
        assertThat(exists).isTrue();
        mockServer.verify();
    }

    @Test
    void testCreateGroup() {
        // Setup
        String responseJson = "{\"id\":1,\"name\":\"NewGroup\",\"path\":\"new-group\",\"full_path\":\"new-group\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/groups"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.path").value("new-group"))
                .andExpect(jsonPath("$.name").value("NewGroup"))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        // Execute
        GitLabGroup group = apiClient.createGroup("new-group", "NewGroup", null);

        // Verify
        assertThat(group).isNotNull();
        assertThat(group.getId()).isEqualTo(1L);
        assertThat(group.getName()).isEqualTo("NewGroup");
        mockServer.verify();
    }

    @Test
    void testCreateProject() {
        // Setup
        String groupJson = "{\"id\":1,\"name\":\"Group1\",\"path\":\"group1\"}";
        String projectJson = "{\"id\":123,\"name\":\"NewProject\",\"path\":\"new-project\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/groups/group1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(groupJson, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.path").value("new-project"))
                .andExpect(jsonPath("$.name").value("NewProject"))
                .andExpect(jsonPath("$.namespace_id").value(1))
                .andRespond(withSuccess(projectJson, MediaType.APPLICATION_JSON));

        // Execute
        GitLabProject project = apiClient.createProject("new-project", "NewProject", "group1");

        // Verify
        assertThat(project).isNotNull();
        assertThat(project.getId()).isEqualTo(123L);
        assertThat(project.getName()).isEqualTo("NewProject");
        mockServer.verify();
    }

    @Test
    void testCreateMirror() {
        // Setup
        String mirrorJson = "{\"id\":1,\"enabled\":true,\"url\":\"http://target.com/repo.git\",\"update_status\":\"finished\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123/remote_mirrors"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.url").value("http://target.com/repo.git"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andRespond(withSuccess(mirrorJson, MediaType.APPLICATION_JSON));

        // Execute
        RemoteMirror mirror = apiClient.createMirror(123L, "http://target.com/repo.git", false);

        // Verify
        assertThat(mirror).isNotNull();
        assertThat(mirror.getId()).isEqualTo(1L);
        assertThat(mirror.getEnabled()).isTrue();
        mockServer.verify();
    }

    @Test
    void testGetMirrors() {
        // Setup
        String mirrorsJson = "[{\"id\":1,\"url\":\"http://mirror1.com\",\"update_status\":\"finished\"}," +
                "{\"id\":2,\"url\":\"http://mirror2.com\",\"update_status\":\"finished\"}]";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123/remote_mirrors"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mirrorsJson, MediaType.APPLICATION_JSON));

        // Execute
        List<RemoteMirror> mirrors = apiClient.getMirrors(123L);

        // Verify
        assertThat(mirrors).hasSize(2);
        assertThat(mirrors.get(0).getId()).isEqualTo(1L);
        assertThat(mirrors.get(1).getId()).isEqualTo(2L);
        mockServer.verify();
    }

    @Test
    void testGetMirror() {
        // Setup
        String mirrorJson = "{\"id\":1,\"url\":\"http://mirror1.com\",\"update_status\":\"finished\"}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123/remote_mirrors/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mirrorJson, MediaType.APPLICATION_JSON));

        // Execute
        RemoteMirror mirror = apiClient.getMirror(123L, 1L);

        // Verify
        assertThat(mirror).isNotNull();
        assertThat(mirror.getId()).isEqualTo(1L);
        assertThat(mirror.getUpdateStatus()).isEqualTo("finished");
        mockServer.verify();
    }

    @Test
    void testDeleteMirror() {
        // Setup
        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123/remote_mirrors/1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        // Execute
        apiClient.deleteMirror(123L, 1L);

        // Verify
        mockServer.verify();
    }

    @Test
    void testGetBranches() {
        // Setup
        String branchesJson = "[{\"name\":\"main\",\"commit\":{\"id\":\"abc123\"}}," +
                "{\"name\":\"develop\",\"commit\":{\"id\":\"def456\"}}]";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123/repository/branches"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(branchesJson, MediaType.APPLICATION_JSON));

        // Execute
        List<RepositoryBranch> branches = apiClient.getBranches(123L);

        // Verify
        assertThat(branches).hasSize(2);
        assertThat(branches.get(0).getName()).isEqualTo("main");
        assertThat(branches.get(1).getName()).isEqualTo("develop");
        mockServer.verify();
    }

    @Test
    void testGetDefaultBranch() {
        // Setup
        String projectJson = "{\"id\":123,\"default_branch\":\"main\"}";
        String branchJson = "{\"name\":\"main\",\"commit\":{\"id\":\"abc123\"}}";

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/group1%252Fproject1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(projectJson, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(BASE_URL + "/api/v4/projects/123/repository/branches/main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(branchJson, MediaType.APPLICATION_JSON));

        // Execute
        RepositoryBranch branch = apiClient.getDefaultBranch("group1/project1");

        // Verify
        assertThat(branch).isNotNull();
        assertThat(branch.getName()).isEqualTo("main");
        mockServer.verify();
    }
}
