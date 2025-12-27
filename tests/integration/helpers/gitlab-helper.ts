/**
 * GitLab API Helper for Integration Tests
 *
 * Provides utilities to interact with GitLab API for testing
 */

const SOURCE_GITLAB_URL = process.env.SOURCE_GITLAB_URL || 'http://localhost:8000'
const SOURCE_GITLAB_TOKEN = process.env.SOURCE_GITLAB_TOKEN || 'glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq'
const TARGET_GITLAB_URL = process.env.TARGET_GITLAB_URL || 'http://localhost:9000'
const TARGET_GITLAB_TOKEN = process.env.TARGET_GITLAB_TOKEN || 'glpat-b2nrFAAy9q2SozZr3Dm0N286MQp1OjEH.01.0w0t2khzm'

export interface GitLabProject {
  id: number
  name: string
  path: string
  path_with_namespace: string
  default_branch: string
  web_url: string
}

export interface GitLabBranch {
  name: string
  commit: {
    id: string
    short_id: string
    title: string
    message: string
    author_name: string
    committed_date: string
  }
  protected: boolean
  default: boolean
}

export interface GitLabCommit {
  id: string
  short_id: string
  title: string
  message: string
  author_name: string
  committed_date: string
}

export class GitLabHelper {
  private baseUrl: string
  private token: string

  constructor(isSource: boolean = true) {
    this.baseUrl = isSource ? SOURCE_GITLAB_URL : TARGET_GITLAB_URL
    this.token = isSource ? SOURCE_GITLAB_TOKEN : TARGET_GITLAB_TOKEN
  }

  /**
   * Get project by path
   */
  async getProject(projectPath: string): Promise<GitLabProject | null> {
    const encodedPath = encodeURIComponent(projectPath)
    const response = await fetch(`${this.baseUrl}/api/v4/projects/${encodedPath}`, {
      headers: {
        'PRIVATE-TOKEN': this.token
      }
    })

    if (response.status === 404) {
      return null
    }

    if (!response.ok) {
      throw new Error(`Failed to get project: ${response.statusText}`)
    }

    return await response.json()
  }

  /**
   * Create a new project
   */
  async createProject(groupPath: string, projectName: string, description?: string): Promise<GitLabProject> {
    // Get group ID first
    const groupResponse = await fetch(`${this.baseUrl}/api/v4/groups/${encodeURIComponent(groupPath)}`, {
      headers: {
        'PRIVATE-TOKEN': this.token
      }
    })

    if (!groupResponse.ok) {
      throw new Error(`Failed to get group: ${groupResponse.statusText}`)
    }

    const group = await groupResponse.json()

    // Create project
    const response = await fetch(`${this.baseUrl}/api/v4/projects`, {
      method: 'POST',
      headers: {
        'PRIVATE-TOKEN': this.token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        name: projectName,
        namespace_id: group.id,
        description: description || `Test project created at ${new Date().toISOString()}`,
        initialize_with_readme: true
      })
    })

    if (!response.ok) {
      const error = await response.text()
      throw new Error(`Failed to create project: ${error}`)
    }

    return await response.json()
  }

  /**
   * Delete a project
   */
  async deleteProject(projectId: number): Promise<void> {
    const response = await fetch(`${this.baseUrl}/api/v4/projects/${projectId}`, {
      method: 'DELETE',
      headers: {
        'PRIVATE-TOKEN': this.token
      }
    })

    if (!response.ok && response.status !== 404) {
      throw new Error(`Failed to delete project: ${response.statusText}`)
    }
  }

  /**
   * Create a commit in a project
   */
  async createCommit(
    projectId: number,
    branchName: string,
    message: string,
    filePath?: string
  ): Promise<GitLabCommit> {
    const timestamp = Date.now()
    const actualFilePath = filePath || `test_file_${timestamp}.txt`

    const response = await fetch(`${this.baseUrl}/api/v4/projects/${projectId}/repository/commits`, {
      method: 'POST',
      headers: {
        'PRIVATE-TOKEN': this.token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        branch: branchName,
        commit_message: message,
        actions: [
          {
            action: 'create',
            file_path: actualFilePath,
            content: `Test content created at ${new Date().toISOString()}`
          }
        ]
      })
    })

    if (!response.ok) {
      const error = await response.text()
      throw new Error(`Failed to create commit: ${error}`)
    }

    return await response.json()
  }

  /**
   * Create a new branch
   */
  async createBranch(projectId: number, branchName: string, ref: string = 'master'): Promise<GitLabBranch> {
    const response = await fetch(`${this.baseUrl}/api/v4/projects/${projectId}/repository/branches`, {
      method: 'POST',
      headers: {
        'PRIVATE-TOKEN': this.token,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        branch: branchName,
        ref: ref
      })
    })

    if (!response.ok) {
      const error = await response.text()
      throw new Error(`Failed to create branch: ${error}`)
    }

    return await response.json()
  }

  /**
   * Get branch information
   */
  async getBranch(projectId: number, branchName: string): Promise<GitLabBranch | null> {
    const encodedBranch = encodeURIComponent(branchName)
    const response = await fetch(`${this.baseUrl}/api/v4/projects/${projectId}/repository/branches/${encodedBranch}`, {
      headers: {
        'PRIVATE-TOKEN': this.token
      }
    })

    if (response.status === 404) {
      return null
    }

    if (!response.ok) {
      throw new Error(`Failed to get branch: ${response.statusText}`)
    }

    return await response.json()
  }

  /**
   * Get all branches
   */
  async getBranches(projectId: number): Promise<GitLabBranch[]> {
    const response = await fetch(`${this.baseUrl}/api/v4/projects/${projectId}/repository/branches`, {
      headers: {
        'PRIVATE-TOKEN': this.token
      }
    })

    if (!response.ok) {
      throw new Error(`Failed to get branches: ${response.statusText}`)
    }

    return await response.json()
  }

  /**
   * Delete a branch
   */
  async deleteBranch(projectId: number, branchName: string): Promise<void> {
    const encodedBranch = encodeURIComponent(branchName)
    const response = await fetch(`${this.baseUrl}/api/v4/projects/${projectId}/repository/branches/${encodedBranch}`, {
      method: 'DELETE',
      headers: {
        'PRIVATE-TOKEN': this.token
      }
    })

    if (!response.ok && response.status !== 404) {
      throw new Error(`Failed to delete branch: ${response.statusText}`)
    }
  }

  /**
   * Wait for branch to have specific commit (polls until found or timeout)
   */
  async waitForCommit(
    projectId: number,
    branchName: string,
    expectedCommitId: string,
    timeoutMs: number = 30000,
    checkIntervalMs: number = 1000
  ): Promise<GitLabBranch> {
    const startTime = Date.now()

    while (Date.now() - startTime < timeoutMs) {
      const branch = await this.getBranch(projectId, branchName)
      if (branch && branch.commit.id === expectedCommitId) {
        return branch
      }
      await new Promise(resolve => setTimeout(resolve, checkIntervalMs))
    }

    throw new Error(`Timeout waiting for commit ${expectedCommitId} after ${timeoutMs}ms`)
  }
}

export const sourceGitLab = new GitLabHelper(true)
export const targetGitLab = new GitLabHelper(false)
