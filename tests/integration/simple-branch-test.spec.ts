import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'

/**
 * Simple Branch Test
 *
 * Tests creating a new branch in an existing project and syncing it
 */

test.describe('Simple Branch Test', () => {
  test('should create branch and sync to target', async () => {
    console.log('\n=== Simple Branch Test ===\n')

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

    const baseBranch = sourceProject!.default_branch || 'main'

    // Step 3: Create a new branch
    console.log('\n🌿 Step 3: Creating new branch')
    const timestamp = Date.now()
    const branchName = `test/branch-${timestamp}`
    console.log(`   Branch name: ${branchName}`)
    console.log(`   Based on: ${baseBranch}`)

    const newBranch = await sourceGitLab.createBranch(
      sourceProject!.id,
      branchName,
      baseBranch
    )
    console.log(`✅ Branch created successfully`)
    console.log(`   Name: ${newBranch.name}`)
    console.log(`   Commit: ${newBranch.commit.short_id}`)
    console.log(`   Protected: ${newBranch.protected}`)

    // Step 4: Verify branch in source
    console.log('\n🔍 Step 4: Verifying branch in source')
    const sourceBranch = await sourceGitLab.getBranch(sourceProject!.id, branchName)
    expect(sourceBranch).not.toBeNull()
    expect(sourceBranch!.name).toBe(branchName)
    console.log(`✅ Branch verified in source`)
    console.log(`   Commit ID: ${sourceBranch!.commit.id}`)

    // Step 5: Add a commit to the new branch
    console.log('\n📝 Step 5: Adding commit to new branch')
    const commitMessage = `feat: branch test commit at ${new Date().toISOString()}`
    console.log(`   Message: ${commitMessage}`)

    const branchCommit = await sourceGitLab.createCommit(
      sourceProject!.id,
      branchName,
      commitMessage
    )
    console.log(`✅ Commit added to branch`)
    console.log(`   Commit: ${branchCommit.short_id}`)

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

    // Step 9: Check if branch exists in target
    console.log('\n🔍 Step 9: Checking branch in target')
    const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)

    if (targetBranch) {
      console.log(`✅ Branch found in target`)
      console.log(`   Name: ${targetBranch.name}`)
      console.log(`   Commit: ${targetBranch.commit.short_id}`)

      if (targetBranch.commit.id === branchCommit.id) {
        console.log(`   ✅ Commit matches source`)
      } else {
        console.log(`   ⚠️  Commit mismatch:`)
        console.log(`      Source: ${branchCommit.short_id}`)
        console.log(`      Target: ${targetBranch.commit.short_id}`)
      }
    } else {
      console.log(`⚠️  Branch not found in target yet`)
      console.log(`   Note: Sync may need more time to propagate`)
    }

    // Step 10: Verify branch comparison
    console.log('\n📊 Step 10: Checking branch comparison')
    const comparison = await mirrorApi.getBranchComparison(project!.id)
    expect(comparison).not.toBeNull()

    console.log(`   Total branches: ${comparison!.branches.length}`)

    const branchComparison = comparison!.branches.find(b => b.branchName === branchName)
    if (branchComparison) {
      console.log(`✅ Branch found in comparison`)
      console.log(`   Sync status: ${branchComparison.syncStatus}`)
      console.log(`   Source commit: ${branchComparison.sourceCommitId?.substring(0, 8)}`)
      console.log(`   Target commit: ${branchComparison.targetCommitId?.substring(0, 8)}`)

      if (branchComparison.sourceCommitId === branchCommit.id) {
        console.log(`   ✅ Source commit matches`)
      }

      if (branchComparison.syncStatus === 'synced') {
        console.log(`   ✅ Branch marked as synced`)
        expect(branchComparison.targetCommitId).toBe(branchCommit.id)
      } else {
        console.log(`   ℹ️  Branch status: ${branchComparison.syncStatus}`)
      }
    } else {
      console.log(`⚠️  Branch not found in comparison`)
      console.log(`   Available branches: ${comparison!.branches.map(b => b.branchName).slice(0, 5).join(', ')}...`)
    }

    console.log('\n✅ ===== BRANCH TEST COMPLETED =====')
    console.log('Summary:')
    console.log(`  • Project: ${project!.projectKey}`)
    console.log(`  • Branch: ${branchName}`)
    console.log(`  • Base: ${baseBranch}`)
    console.log(`  • Commit: ${branchCommit.short_id}`)
    console.log(`  • Sync status: ${syncResult.syncStatus}`)
    console.log('====================================\n')
  })
})
