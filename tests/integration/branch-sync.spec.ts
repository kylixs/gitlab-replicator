import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'

/**
 * Integration Test: Branch Creation Sync
 *
 * Tests that new branches created in source GitLab are automatically synced to target GitLab
 */

test.describe('Branch Creation Sync Integration Tests', () => {
  const testGroups = ['ai', 'arch']

  for (const groupName of testGroups) {
    test(`should sync new branch in ${groupName} group project`, async () => {
      console.log(`\n=== Testing branch creation sync for ${groupName} group ===`)

      // Step 1: Find an existing project in the group
      const projects = await mirrorApi.getProjects({ group: groupName })
      expect(projects.items.length).toBeGreaterThan(0)

      const project = projects.items[0]
      console.log(`Using project: ${project.projectKey}`)

      // Step 2: Get source GitLab project
      const sourceProject = await sourceGitLab.getProject(project.projectKey)
      expect(sourceProject).not.toBeNull()
      console.log(`Source project ID: ${sourceProject!.id}`)

      // Step 3: Create a new branch
      const timestamp = Date.now()
      const branchName = `test/integration-${timestamp}`
      const baseBranch = sourceProject!.default_branch || 'master'
      console.log(`Creating branch: ${branchName} from ${baseBranch}`)

      const newBranch = await sourceGitLab.createBranch(
        sourceProject!.id,
        branchName,
        baseBranch
      )
      console.log(`New branch created: ${newBranch.name}`)
      console.log(`Branch commit: ${newBranch.commit.short_id}`)

      // Step 4: Trigger manual sync
      console.log(`Triggering sync for project ${project.id}...`)
      await mirrorApi.triggerSync(project.id)

      // Step 5: Wait for sync to complete
      console.log('Waiting for sync to complete...')
      const syncResult = await mirrorApi.waitForSync(project.id, 60000)
      console.log(`Sync completed with status: ${syncResult.syncStatus}`)

      // Step 6: Verify sync result
      expect(['success', 'skipped']).toContain(syncResult.syncStatus)

      // Step 7: Verify target GitLab has the new branch
      const targetProject = await targetGitLab.getProject(project.projectKey)
      expect(targetProject).not.toBeNull()

      const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)
      expect(targetBranch).not.toBeNull()
      expect(targetBranch!.commit.id).toBe(newBranch.commit.id)
      console.log(`✅ Target branch created: ${targetBranch!.name}`)

      // Step 8: Verify branch comparison
      const comparison = await mirrorApi.getBranchComparison(project.id)
      expect(comparison).not.toBeNull()

      const branch = comparison!.branches.find(b => b.branchName === branchName)
      expect(branch).toBeDefined()
      expect(branch!.syncStatus).toBe('synced')
      expect(branch!.sourceCommitId).toBe(newBranch.commit.id)
      expect(branch!.targetCommitId).toBe(newBranch.commit.id)
      expect(branch!.sourceCommitAuthor).toBeTruthy()
      expect(branch!.sourceLastCommitAt).toBeTruthy()
      expect(branch!.targetCommitAuthor).toBeTruthy()
      expect(branch!.targetLastCommitAt).toBeTruthy()
      console.log(`✅ Branch comparison verified: ${branch!.syncStatus}`)
      console.log(`   Source author: ${branch!.sourceCommitAuthor}`)
      console.log(`   Target author: ${branch!.targetCommitAuthor}`)

      // Step 9: Cleanup - delete the test branch
      console.log(`Cleaning up test branch: ${branchName}`)
      await sourceGitLab.deleteBranch(sourceProject!.id, branchName)
      await targetGitLab.deleteBranch(targetProject!.id, branchName)

      console.log(`\n✅ Branch creation sync test passed for ${groupName} group\n`)
    })

    test(`should sync commit to new branch in ${groupName} group project`, async () => {
      console.log(`\n=== Testing commit to new branch sync for ${groupName} group ===`)

      // Step 1: Find an existing project in the group
      const projects = await mirrorApi.getProjects({ group: groupName })
      expect(projects.items.length).toBeGreaterThan(0)

      const project = projects.items[0]
      console.log(`Using project: ${project.projectKey}`)

      // Step 2: Get source GitLab project
      const sourceProject = await sourceGitLab.getProject(project.projectKey)
      expect(sourceProject).not.toBeNull()

      // Step 3: Create a new branch
      const timestamp = Date.now()
      const branchName = `test/commit-branch-${timestamp}`
      const baseBranch = sourceProject!.default_branch || 'master'
      console.log(`Creating branch: ${branchName}`)

      const newBranch = await sourceGitLab.createBranch(
        sourceProject!.id,
        branchName,
        baseBranch
      )
      console.log(`Branch created: ${newBranch.commit.short_id}`)

      // Step 4: Create a commit on the new branch
      const commitMessage = `test: commit on new branch at ${new Date().toISOString()}`
      console.log(`Creating commit: ${commitMessage}`)

      const newCommit = await sourceGitLab.createCommit(
        sourceProject!.id,
        branchName,
        commitMessage
      )
      console.log(`Commit created: ${newCommit.short_id}`)

      // Step 5: Trigger manual sync
      console.log(`Triggering sync for project ${project.id}...`)
      await mirrorApi.triggerSync(project.id)

      // Step 6: Wait for sync to complete
      console.log('Waiting for sync to complete...')
      const syncResult = await mirrorApi.waitForSync(project.id, 60000)
      console.log(`Sync completed with status: ${syncResult.syncStatus}`)

      // Step 7: Verify target GitLab has the branch with new commit
      const targetProject = await targetGitLab.getProject(project.projectKey)
      expect(targetProject).not.toBeNull()

      const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)
      expect(targetBranch).not.toBeNull()
      expect(targetBranch!.commit.id).toBe(newCommit.id)
      console.log(`✅ Target branch has new commit: ${targetBranch!.commit.short_id}`)

      // Step 8: Verify branch comparison
      const comparison = await mirrorApi.getBranchComparison(project.id)
      expect(comparison).not.toBeNull()

      const branch = comparison!.branches.find(b => b.branchName === branchName)
      expect(branch).toBeDefined()
      expect(branch!.syncStatus).toBe('synced')
      expect(branch!.sourceCommitId).toBe(newCommit.id)
      expect(branch!.targetCommitId).toBe(newCommit.id)
      console.log(`✅ Branch comparison verified: synced`)

      // Step 9: Cleanup
      console.log(`Cleaning up test branch: ${branchName}`)
      await sourceGitLab.deleteBranch(sourceProject!.id, branchName)
      await targetGitLab.deleteBranch(targetProject!.id, branchName)

      console.log(`\n✅ Commit to new branch sync test passed for ${groupName} group\n`)
    })
  }
})
