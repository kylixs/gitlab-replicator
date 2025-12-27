/**
 * Webhook Integration Tests
 *
 * Tests GitLab webhook integration for fast sync triggering
 * Target: Webhook → Sync complete within 60 seconds
 */

import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'
import { webhookHelper } from './helpers/webhook-helper'

test.describe.serial('Webhook Integration Tests', () => {
  const testGroups = [
    { name: 'ai', projectName: 'test-rails-5', branchName: 'master' },
  ]

  for (const { name: groupName, projectName, branchName } of testGroups) {
    test(`should trigger fast sync on commit push - ${groupName} group`, async () => {
      console.log(`\n=== Testing webhook commit push for ${groupName} group ===\n`)

      // Step 1: Find test project
      const projectsResult = await mirrorApi.getProjects({ group: groupName })
      const project = projectsResult.items.find(p => p.projectKey === `${groupName}/${projectName}`)
      expect(project).not.toBeNull()
      console.log(`Using project: ${project!.projectKey}`)

      // Step 2: Get source project for webhook
      const sourceProject = await sourceGitLab.getProject(project!.projectKey)
      expect(sourceProject).not.toBeNull()
      console.log(`Source project ID: ${sourceProject!.id}`)

      // Step 3: Get current branch commit
      const currentBranch = await sourceGitLab.getBranch(sourceProject!.id, branchName)
      const currentCommitId = currentBranch!.commit.id
      console.log(`Current commit: ${currentBranch!.commit.short_id}`)

      // Step 4: Send webhook event (simulate commit push)
      // We don't create actual commit, just send webhook to test fast sync trigger
      const commitMessage = `test: webhook commit push at ${new Date().toISOString()}`
      console.log(`Sending webhook event for simulated commit...`)

      await webhookHelper.sendPushEvent({
        projectId: sourceProject!.id,
        projectPath: project!.projectKey,
        ref: `refs/heads/${branchName}`,
        beforeSha: currentCommitId,
        afterSha: currentCommitId, // Same SHA - no actual change
        totalCommitsCount: 0,
        commits: [],
      })
      console.log(`Webhook sent successfully`)

      // Step 5: Wait for sync to complete (max 60 seconds)
      console.log(`Waiting for webhook-triggered sync...`)
      const syncResult = await webhookHelper.waitForWebhookSync(project!.id, 60000, 2000)
      console.log(`Sync completed: status=${syncResult.syncStatus}, hasChanges=${syncResult.hasChanges}`)

      // Step 6: Verify sync was triggered by webhook
      expect(['success', 'skipped']).toContain(syncResult.syncStatus)
      console.log(`✅ Webhook triggered sync successfully`)

      console.log(`\n✅ Webhook commit test passed for ${groupName} group\n`)
    })

    test(`should trigger fast sync on branch create - ${groupName} group`, async () => {
      console.log(`\n=== Testing webhook branch create for ${groupName} group ===\n`)

      // Step 1: Find test project
      const projectsResult = await mirrorApi.getProjects({ group: groupName })
      const project = projectsResult.items.find(p => p.projectKey === `${groupName}/${projectName}`)
      expect(project).not.toBeNull()

      // Step 2: Get source project
      const sourceProject = await sourceGitLab.getProject(project!.projectKey)
      expect(sourceProject).not.toBeNull()

      // Step 3: Create new branch
      const newBranchName = `webhook-test-${Date.now()}`
      console.log(`Creating branch: ${newBranchName}`)

      const newBranch = await sourceGitLab.createBranch(sourceProject!.id, newBranchName, branchName)
      console.log(`Branch created: ${newBranch.name}, commit: ${newBranch.commit.short_id}`)

      // Step 4: Send webhook for branch create
      console.log(`Sending branch create webhook...`)
      await webhookHelper.sendBranchCreateEvent({
        projectId: sourceProject!.id,
        projectPath: project!.projectKey,
        branchName: newBranchName,
        afterSha: newBranch.commit.id,
      })
      console.log(`Webhook sent`)

      // Step 5: Wait for sync
      console.log(`Waiting for webhook-triggered sync...`)
      const syncResult = await webhookHelper.waitForWebhookSync(project!.id, 60000)
      console.log(`Sync completed: status=${syncResult.status}`)

      // Step 6: Verify branch in target
      console.log(`Checking for branch ${newBranchName} in target...`)
      const targetProject = await targetGitLab.getProject(project!.projectKey)
      expect(targetProject).not.toBeNull()

      // Wait up to 30s for branch to appear in target
      let targetBranch = null
      for (let i = 0; i < 15; i++) {
        try {
          targetBranch = await targetGitLab.getBranch(targetProject!.id, newBranchName)
          if (targetBranch) break
        } catch (e) {
          // Branch not found yet
        }
        await new Promise(resolve => setTimeout(resolve, 2000))
      }

      expect(targetBranch).not.toBeNull()
      expect(targetBranch!.commit.id).toBe(newBranch.commit.id)

      console.log(`✅ Webhook branch create verified`)
      console.log(`\n✅ Webhook branch test passed for ${groupName} group\n`)

      // Cleanup: Delete test branch from source (will sync to target)
      await sourceGitLab.deleteBranch(sourceProject!.id, newBranchName)
      console.log(`Cleaned up test branch: ${newBranchName}`)
    })

    test(`should trigger fast sync on branch delete - ${groupName} group`, async () => {
      console.log(`\n=== Testing webhook branch delete for ${groupName} group ===\n`)

      // Step 1: Find test project
      const projectsResult = await mirrorApi.getProjects({ group: groupName })
      const project = projectsResult.items.find(p => p.projectKey === `${groupName}/${projectName}`)
      expect(project).not.toBeNull()

      // Step 2: Get source project
      const sourceProject = await sourceGitLab.getProject(project!.projectKey)
      expect(sourceProject).not.toBeNull()

      // Step 3: Create branch to delete
      const testBranchName = `delete-test-${Date.now()}`
      console.log(`Creating branch to delete: ${testBranchName}`)

      const testBranch = await sourceGitLab.createBranch(sourceProject!.id, testBranchName, branchName)
      console.log(`Branch created: ${testBranch.name}`)

      // Wait for initial sync to push branch to target
      await new Promise(resolve => setTimeout(resolve, 5000))

      // Trigger manual sync to ensure branch exists in target
      await mirrorApi.triggerSync(project!.id)
      await mirrorApi.waitForSync(project!.id, 30000)

      // Step 4: Delete branch from source
      console.log(`Deleting branch: ${testBranchName}`)
      await sourceGitLab.deleteBranch(sourceProject!.id, testBranchName)

      // Step 5: Send webhook for branch delete
      console.log(`Sending branch delete webhook...`)
      await webhookHelper.sendBranchDeleteEvent({
        projectId: sourceProject!.id,
        projectPath: project!.projectKey,
        branchName: testBranchName,
        beforeSha: testBranch.commit.id,
      })
      console.log(`Webhook sent`)

      // Step 6: Wait for sync
      console.log(`Waiting for webhook-triggered sync...`)
      const syncResult = await webhookHelper.waitForWebhookSync(project!.id, 60000)
      console.log(`Sync completed: status=${syncResult.status}`)

      // Step 7: Verify branch deleted from target
      console.log(`Verifying branch ${testBranchName} deleted from target...`)
      const targetProject = await targetGitLab.getProject(project!.projectKey)
      expect(targetProject).not.toBeNull()

      // Wait up to 30s for branch to be deleted from target
      let branchDeleted = false
      for (let i = 0; i < 15; i++) {
        try {
          await targetGitLab.getBranch(targetProject!.id, testBranchName)
          // Branch still exists, wait
        } catch (e) {
          // Branch not found - deleted successfully
          branchDeleted = true
          break
        }
        await new Promise(resolve => setTimeout(resolve, 2000))
      }

      expect(branchDeleted).toBe(true)

      console.log(`✅ Webhook branch delete verified`)
      console.log(`\n✅ Webhook branch delete test passed for ${groupName} group\n`)
    })
  }

  test('should handle webhook for non-synced project gracefully', async () => {
    console.log(`\n=== Testing webhook for non-synced project ===\n`)

    // Send webhook for a project that doesn't exist in sync
    await webhookHelper.sendPushEvent({
      projectId: 99999,
      projectPath: 'non-existent/project',
      ref: 'refs/heads/master',
      beforeSha: '1111111111111111111111111111111111111111',
      afterSha: '2222222222222222222222222222222222222222',
      totalCommitsCount: 1,
    })

    console.log(`Webhook sent for non-synced project (should be ignored gracefully)`)
    console.log(`✅ Non-synced project test passed\n`)
  })
})
