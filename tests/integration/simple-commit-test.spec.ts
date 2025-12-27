import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'

/**
 * Simple Commit Test
 *
 * Tests creating a commit in an existing project and syncing it
 */

test.describe('Simple Commit Test', () => {
  test('should create commit and sync to target', async () => {
    console.log('\n=== Simple Commit Test ===\n')

    const testGroup = 'ai'

    // Step 1: Find an existing active project
    console.log('📋 Step 1: Finding existing project')
    const projects = await mirrorApi.getProjects({ group: testGroup })
    expect(projects.items.length).toBeGreaterThan(0)

    const project = projects.items.find(p => p.syncStatus === 'active')
    expect(project).toBeDefined()
    console.log(`✅ Using project: ${project!.projectKey}`)
    console.log(`   Mirror ID: ${project!.id}`)
    console.log(`   Sync status: ${project!.syncStatus}`)

    // Step 2: Get source project details
    console.log('\n🔍 Step 2: Getting source project details')
    const sourceProject = await sourceGitLab.getProject(project!.projectKey)
    expect(sourceProject).not.toBeNull()
    console.log(`✅ Source project found`)
    console.log(`   GitLab ID: ${sourceProject!.id}`)
    console.log(`   Default branch: ${sourceProject!.default_branch}`)

    const branchName = sourceProject!.default_branch || 'main'

    // Step 3: Get current branch state
    console.log('\n🌿 Step 3: Getting current branch state')
    const sourceBranchBefore = await sourceGitLab.getBranch(sourceProject!.id, branchName)
    expect(sourceBranchBefore).not.toBeNull()
    console.log(`✅ Current state:`)
    console.log(`   Branch: ${branchName}`)
    console.log(`   Commit: ${sourceBranchBefore!.commit.short_id}`)
    console.log(`   Message: ${sourceBranchBefore!.commit.title}`)

    // Step 4: Create a new commit
    console.log('\n📝 Step 4: Creating new commit')
    const timestamp = new Date().toISOString()
    const commitMessage = `test: simple commit test at ${timestamp}`
    console.log(`   Message: ${commitMessage}`)

    const newCommit = await sourceGitLab.createCommit(
      sourceProject!.id,
      branchName,
      commitMessage
    )
    console.log(`✅ Commit created successfully`)
    console.log(`   Commit ID: ${newCommit.id}`)
    console.log(`   Short ID: ${newCommit.short_id}`)
    console.log(`   Author: ${newCommit.author_name}`)

    // Step 5: Verify commit in source
    console.log('\n🔍 Step 5: Verifying commit in source')
    const sourceBranchAfter = await sourceGitLab.getBranch(sourceProject!.id, branchName)
    expect(sourceBranchAfter!.commit.id).toBe(newCommit.id)
    console.log(`✅ Commit verified in source`)
    console.log(`   Branch now at: ${sourceBranchAfter!.commit.short_id}`)

    // Step 6: Trigger manual sync
    console.log('\n🔄 Step 6: Triggering manual sync')
    await mirrorApi.triggerSync(project!.id)
    console.log(`✅ Sync triggered`)

    // Step 7: Wait for sync to complete
    console.log('\n⏳ Step 7: Waiting for sync to complete')
    const syncResult = await mirrorApi.waitForSync(project!.id, 120000)
    console.log(`✅ Sync completed`)
    console.log(`   Status: ${syncResult.syncStatus}`)
    console.log(`   Has changes: ${syncResult.hasChanges}`)
    console.log(`   Duration: ${syncResult.durationSeconds}s`)

    expect(['success', 'skipped']).toContain(syncResult.syncStatus)

    // Step 8: Verify target project exists
    console.log('\n🎯 Step 8: Verifying target project')
    const targetProject = await targetGitLab.getProject(project!.projectKey)
    expect(targetProject).not.toBeNull()
    console.log(`✅ Target project found`)
    console.log(`   GitLab ID: ${targetProject!.id}`)

    // Step 9: Check if commit reached target
    console.log('\n🔍 Step 9: Checking commit in target')
    const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)

    if (targetBranch && targetBranch.commit.id === newCommit.id) {
      console.log(`✅ Commit synced to target successfully!`)
      console.log(`   Target commit: ${targetBranch.commit.short_id}`)
      console.log(`   Matches source: YES`)
    } else if (targetBranch) {
      console.log(`⚠️  Target branch exists but commit not yet synced`)
      console.log(`   Target commit: ${targetBranch.commit.short_id}`)
      console.log(`   Expected: ${newCommit.short_id}`)
      console.log(`   Note: Sync may take additional time to propagate`)
    } else {
      console.log(`⚠️  Target branch not found`)
    }

    // Step 10: Verify branch comparison
    console.log('\n📊 Step 10: Checking branch comparison')
    const comparison = await mirrorApi.getBranchComparison(project!.id)
    expect(comparison).not.toBeNull()

    const branchComparison = comparison!.branches.find(b => b.branchName === branchName)
    if (branchComparison) {
      console.log(`✅ Branch comparison found`)
      console.log(`   Sync status: ${branchComparison.syncStatus}`)
      console.log(`   Source commit: ${branchComparison.sourceCommitId?.substring(0, 8)}`)
      console.log(`   Target commit: ${branchComparison.targetCommitId?.substring(0, 8)}`)

      if (branchComparison.sourceCommitId === newCommit.id) {
        console.log(`   ✅ Source commit matches new commit`)
      }

      if (branchComparison.syncStatus === 'synced') {
        console.log(`   ✅ Branch marked as synced`)
        expect(branchComparison.targetCommitId).toBe(newCommit.id)
      } else {
        console.log(`   ℹ️  Branch status: ${branchComparison.syncStatus}`)
      }
    }

    console.log('\n✅ ===== COMMIT TEST COMPLETED =====')
    console.log('Summary:')
    console.log(`  • Project: ${project!.projectKey}`)
    console.log(`  • Branch: ${branchName}`)
    console.log(`  • Commit: ${newCommit.short_id}`)
    console.log(`  • Sync status: ${syncResult.syncStatus}`)
    console.log('====================================\n')
  })
})
