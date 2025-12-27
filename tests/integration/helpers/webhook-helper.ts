/**
 * Webhook Helper
 *
 * Utilities for simulating GitLab webhook events in integration tests
 */

import axios from 'axios'

const MIRROR_API_BASE_URL = process.env.MIRROR_API_URL || 'http://localhost:9999'

export class WebhookHelper {
  private webhookUrl: string

  constructor() {
    this.webhookUrl = `${MIRROR_API_BASE_URL}/api/webhooks/gitlab`
  }

  /**
   * Send push event webhook (commit pushed to branch)
   */
  async sendPushEvent(options: {
    projectId: number
    projectPath: string
    ref: string // e.g., "refs/heads/master"
    beforeSha: string
    afterSha: string
    totalCommitsCount?: number
    commits?: Array<{
      id: string
      message: string
      author: { name: string; email: string }
    }>
  }): Promise<void> {
    const payload = {
      object_kind: 'push',
      event_name: 'push',
      before: options.beforeSha,
      after: options.afterSha,
      ref: options.ref,
      checkout_sha: options.afterSha,
      user_id: 1,
      user_name: 'Administrator',
      user_email: 'admin@example.com',
      project_id: options.projectId,
      project: {
        id: options.projectId,
        name: options.projectPath.split('/').pop(),
        path_with_namespace: options.projectPath,
        web_url: `http://localhost:8000/${options.projectPath}`,
      },
      repository: {
        name: options.projectPath.split('/').pop(),
        url: `git@localhost:${options.projectPath}.git`,
        homepage: `http://localhost:8000/${options.projectPath}`,
      },
      commits: options.commits || [],
      total_commits_count: options.totalCommitsCount || (options.commits?.length || 0),
    }

    console.log(`Sending push webhook: ${options.projectPath} ${options.ref} ${options.beforeSha?.substring(0, 8)} → ${options.afterSha?.substring(0, 8)}`)

    await axios.post(this.webhookUrl, payload, {
      headers: {
        'X-Gitlab-Event': 'Push Hook',
        'Content-Type': 'application/json',
      },
    })
  }

  /**
   * Send tag push event webhook
   */
  async sendTagPushEvent(options: {
    projectId: number
    projectPath: string
    ref: string // e.g., "refs/tags/v1.0.0"
    beforeSha: string
    afterSha: string
  }): Promise<void> {
    const payload = {
      object_kind: 'tag_push',
      event_name: 'tag_push',
      before: options.beforeSha,
      after: options.afterSha,
      ref: options.ref,
      checkout_sha: options.afterSha,
      user_id: 1,
      user_name: 'Administrator',
      project_id: options.projectId,
      project: {
        id: options.projectId,
        name: options.projectPath.split('/').pop(),
        path_with_namespace: options.projectPath,
      },
    }

    console.log(`Sending tag push webhook: ${options.projectPath} ${options.ref}`)

    await axios.post(this.webhookUrl, payload, {
      headers: {
        'X-Gitlab-Event': 'Tag Push Hook',
        'Content-Type': 'application/json',
      },
    })
  }

  /**
   * Send branch create webhook (before = 0000..., after = commit SHA)
   */
  async sendBranchCreateEvent(options: {
    projectId: number
    projectPath: string
    branchName: string
    afterSha: string
  }): Promise<void> {
    await this.sendPushEvent({
      projectId: options.projectId,
      projectPath: options.projectPath,
      ref: `refs/heads/${options.branchName}`,
      beforeSha: '0000000000000000000000000000000000000000',
      afterSha: options.afterSha,
      totalCommitsCount: 0,
    })
  }

  /**
   * Send branch delete webhook (before = commit SHA, after = 0000...)
   */
  async sendBranchDeleteEvent(options: {
    projectId: number
    projectPath: string
    branchName: string
    beforeSha: string
  }): Promise<void> {
    await this.sendPushEvent({
      projectId: options.projectId,
      projectPath: options.projectPath,
      ref: `refs/heads/${options.branchName}`,
      beforeSha: options.beforeSha,
      afterSha: '0000000000000000000000000000000000000000',
      totalCommitsCount: 0,
    })
  }

  /**
   * Wait for webhook to trigger sync and complete (max 60 seconds)
   */
  async waitForWebhookSync(
    projectId: number,
    timeoutMs: number = 60000,
    checkIntervalMs: number = 2000
  ): Promise<{ status: string; hasChanges: boolean }> {
    const startTime = Date.now()

    while (Date.now() - startTime < timeoutMs) {
      try {
        // Check sync result
        const response = await axios.get(`${MIRROR_API_BASE_URL}/api/sync/projects/${projectId}/result`)
        const result = response.data.data

        if (result && result.sync_status) {
          // Check if this is a recent sync (within last 2 minutes)
          const syncTime = new Date(result.last_sync_at).getTime()
          if (Date.now() - syncTime < 120000) {
            return {
              status: result.sync_status,
              hasChanges: result.has_changes,
            }
          }
        }
      } catch (error) {
        // Ignore errors, keep polling
      }

      await new Promise(resolve => setTimeout(resolve, checkIntervalMs))
    }

    throw new Error(`Webhook sync timeout after ${timeoutMs}ms for project ${projectId}`)
  }
}

export const webhookHelper = new WebhookHelper()
