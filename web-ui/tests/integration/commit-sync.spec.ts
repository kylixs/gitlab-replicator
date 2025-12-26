import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'

/**
 * Integration Test: Commit Sync
 *
 * Tests that commits made to source GitLab are automatically synced to target GitLab
 */

test.describe('Commit Sync Integration Tests', () => {
  const testGroups = ['ai', 'arch']

  for (const groupName of testGroups) {
    test(`should sync new commit in ${groupName} group project`, async () => {
      console.log(`\n=== Testing commit sync for ${groupName} group ===`)

      // Step 1: Find an existing project in the group
      const projects = await mirrorApi.getProjects({ group: groupName })
      expect(projects.items.length).toBeGreaterThan(0)

      const project = projects.items[0]
      console.log(`Using project: ${project.projectKey}`)

      // Step 2: Get source GitLab project
      const sourceProject = await sourceGitLab.getProject(project.projectKey)
      expect(sourceProject).not.toBeNull()
      console.log(`Source project ID: ${sourceProject!.id}`)

      // Step 3: Get current branch state
      const branchName = sourceProject!.default_branch || 'master'
      const sourceBranchBefore = await sourceGitLab.getBranch(sourceProject!.id, branchName)
      expect(sourceBranchBefore).not.toBeNull()
      console.log(`Current commit: ${sourceBranchBefore!.commit.short_id}`)

      // Step 4: Create a test commit
      const commitMessage = `test: integration test commit at ${new Date().toISOString()}`
      console.log(`Creating commit: ${commitMessage}`)

      const newCommit = await sourceGitLab.createCommit(
        sourceProject!.id,
        branchName,
        commitMessage
      )
      console.log(`New commit created: ${newCommit.short_id}`)

      // Step 5: Trigger manual sync
      console.log(`Triggering sync for project ${project.id}...`)
      await mirrorApi.triggerSync(project.id)

      // Step 6: Wait for sync to complete
      console.log('Waiting for sync to complete...')
      const syncResult = await mirrorApi.waitForSync(project.id, 60000)
      console.log(`Sync completed with status: ${syncResult.syncStatus}`)

      // Step 7: Verify sync result
      expect(syncResult.syncStatus).toBe('success')
      expect(syncResult.hasChanges).toBe(true)
      expect(syncResult.sourceCommitSha).toBe(newCommit.id)

      // Step 8: Verify target GitLab has the new commit
      const targetProject = await targetGitLab.getProject(project.projectKey)
      expect(targetProject).not.toBeNull()

      const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)
      expect(targetBranch).not.toBeNull()
      expect(targetBranch!.commit.id).toBe(newCommit.id)
      console.log(`✅ Target branch updated to: ${targetBranch!.commit.short_id}`)

      // Step 9: Verify branch comparison shows synced
      const comparison = await mirrorApi.getBranchComparison(project.id)
      expect(comparison).not.toBeNull()

      const branch = comparison!.branches.find(b => b.branchName === branchName)
      expect(branch).toBeDefined()
      expect(branch!.syncStatus).toBe('synced')
      expect(branch!.sourceCommitId).toBe(newCommit.id)
      expect(branch!.targetCommitId).toBe(newCommit.id)
      expect(branch!.sourceCommitAuthor).toBeTruthy()
      expect(branch!.sourceLastCommitAt).toBeTruthy()
      console.log(`✅ Branch comparison verified: ${branch!.syncStatus}`)

      console.log(`\n✅ Commit sync test passed for ${groupName} group\n`)
    })
  }
})

test.describe('Auto Sync Tests', () => {
  const testGroups = ['ai', 'arch']

  for (const groupName of testGroups) {
    test(`should auto-sync new commit in ${groupName} group project`, async () => {
      console.log(`\n=== Testing auto-sync for ${groupName} group ===`)

      // Step 1: Find an existing project in the group
      const projects = await mirrorApi.getProjects({ group: groupName })
      expect(projects.items.length).toBeGreaterThan(0)

      const project = projects.items[0]
      console.log(`Using project: ${project.projectKey}`)

      // Step 2: Get source GitLab project
      const sourceProject = await sourceGitLab.getProject(project.projectKey)
      expect(sourceProject).not.toBeNull()

      // Step 3: Create a test commit
      const branchName = sourceProject!.default_branch || 'master'
      const commitMessage = `test: auto-sync test commit at ${new Date().toISOString()}`
      console.log(`Creating commit: ${commitMessage}`)

      const newCommit = await sourceGitLab.createCommit(
        sourceProject!.id,
        branchName,
        commitMessage
      )
      console.log(`New commit created: ${newCommit.short_id}`)

      // Step 4: Wait for auto-sync (scheduled task runs every 5 minutes)
      console.log('Waiting for auto-sync (max 6 minutes)...')
      const syncResult = await mirrorApi.waitForSync(project.id, 360000, 10000)
      console.log(`Auto-sync completed with status: ${syncResult.syncStatus}`)

      // Step 5: Verify sync result
      expect(syncResult.syncStatus).toBe('success')
      expect(syncResult.sourceCommitSha).toBe(newCommit.id)

      // Step 6: Verify target GitLab has the new commit
      const targetProject = await targetGitLab.getProject(project.projectKey)
      expect(targetProject).not.toBeNull()

      const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)
      expect(targetBranch).not.toBeNull()
      expect(targetBranch!.commit.id).toBe(newCommit.id)
      console.log(`✅ Auto-sync verified: ${targetBranch!.commit.short_id}`)

      console.log(`\n✅ Auto-sync test passed for ${groupName} group\n`)
    })
  }
})
