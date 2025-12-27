import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'

/**
 * Full Lifecycle Integration Test
 *
 * Simulates a complete real-world scenario:
 * 1. Create a new project
 * 2. Make commits to the project
 * 3. Create new branches
 * 4. Verify all changes are synced to target
 */

test.describe('Full GitLab Mirror Lifecycle Tests', () => {
  test('should handle full project lifecycle: create → commit → branch → sync', async () => {
    console.log('\n=== Full Lifecycle Test: Create Project → Commit → Branch → Sync ===\n')

    const testGroup = 'ai'
    const timestamp = Date.now()
    const projectName = `lifecycle-test-${timestamp}`
    const projectPath = `${testGroup}/${projectName}`

    // ============================================
    // Step 1: Create a new project in source GitLab
    // ============================================
    console.log('📦 Step 1: Creating new project in source GitLab')
    const sourceProject = await sourceGitLab.createProject(
      testGroup,
      projectName,
      `Full lifecycle integration test project created at ${new Date().toISOString()}`
    )
    console.log(`✅ Source project created: ${sourceProject.path_with_namespace}`)
    console.log(`   Project ID: ${sourceProject.id}`)
    console.log(`   Default branch: ${sourceProject.default_branch}`)

    try {
      let mirrorProject = null
      let targetProject = null
      // ============================================
      // Step 2: Wait for project discovery
      // ============================================
      console.log('\n🔍 Step 2: Waiting for project discovery')
      const maxWaitTime = 360000 // 6 minutes
      const checkInterval = 10000 // 10 seconds
      const startTime = Date.now()

      while (Date.now() - startTime < maxWaitTime) {
        // Use search parameter since path parameter doesn't work
        const projects = await mirrorApi.getProjects({ group: testGroup })
        mirrorProject = projects.items.find(p => p.projectKey === projectPath) || null

        if (mirrorProject) {
          console.log(`✅ Project discovered in mirror system`)
          console.log(`   Mirror ID: ${mirrorProject.id}`)
          console.log(`   Sync status: ${mirrorProject.syncStatus}`)
          break
        }
        console.log(`   Checking... (${Math.floor((Date.now() - startTime) / 1000)}s elapsed)`)
        await new Promise(resolve => setTimeout(resolve, checkInterval))
      }

      expect(mirrorProject).not.toBeNull()
      expect(mirrorProject!.syncStatus).toBeTruthy()

      // ============================================
      // Step 3: Wait for initial sync to complete
      // ============================================
      console.log('\n⚙️  Step 3: Waiting for initial sync to complete')
      const syncResult = await mirrorApi.waitForSync(mirrorProject!.id, 120000)
      console.log(`✅ Initial sync completed`)
      console.log(`   Status: ${syncResult.syncStatus}`)
      console.log(`   Has changes: ${syncResult.hasChanges}`)

      // ============================================
      // Step 4: Verify target project was created
      // ============================================
      console.log('\n🎯 Step 4: Verifying target project')
      targetProject = await targetGitLab.getProject(projectPath)
      expect(targetProject).not.toBeNull()
      console.log(`✅ Target project verified`)
      console.log(`   Target ID: ${targetProject!.id}`)
      console.log(`   Default branch: ${targetProject!.default_branch}`)

      // ============================================
      // Step 5: Create first commit in source
      // ============================================
      console.log('\n📝 Step 5: Creating first commit in source')
      const defaultBranch = sourceProject.default_branch || 'main'
      const commit1Message = `feat: add initial feature - ${new Date().toISOString()}`

      const commit1 = await sourceGitLab.createCommit(
        sourceProject.id,
        defaultBranch,
        commit1Message
      )
      console.log(`✅ Commit 1 created: ${commit1.short_id}`)
      console.log(`   Message: ${commit1Message}`)

      // ============================================
      // Step 6: Trigger sync and verify commit synced
      // ============================================
      console.log('\n🔄 Step 6: Triggering sync for commit 1')
      await mirrorApi.triggerSync(mirrorProject!.id)
      const syncResult2 = await mirrorApi.waitForSync(mirrorProject!.id, 60000)
      console.log(`✅ Sync completed: ${syncResult2.syncStatus}`)

      console.log('   Verifying commit 1 in target...')
      const targetBranch1 = await targetGitLab.waitForCommit(
        targetProject!.id,
        defaultBranch,
        commit1.id,
        30000
      )
      console.log(`✅ Commit 1 synced to target: ${targetBranch1.commit.short_id}`)

      // ============================================
      // Step 7: Create second commit in source
      // ============================================
      console.log('\n📝 Step 7: Creating second commit in source')
      const commit2Message = `fix: bug fix - ${new Date().toISOString()}`

      const commit2 = await sourceGitLab.createCommit(
        sourceProject.id,
        defaultBranch,
        commit2Message
      )
      console.log(`✅ Commit 2 created: ${commit2.short_id}`)
      console.log(`   Message: ${commit2Message}`)

      // ============================================
      // Step 8: Sync and verify commit 2
      // ============================================
      console.log('\n🔄 Step 8: Triggering sync for commit 2')
      await mirrorApi.triggerSync(mirrorProject!.id)
      const syncResult3 = await mirrorApi.waitForSync(mirrorProject!.id, 60000)
      console.log(`✅ Sync completed: ${syncResult3.syncStatus}`)

      console.log('   Verifying commit 2 in target...')
      const targetBranch2 = await targetGitLab.waitForCommit(
        targetProject!.id,
        defaultBranch,
        commit2.id,
        30000
      )
      console.log(`✅ Commit 2 synced to target: ${targetBranch2.commit.short_id}`)

      // ============================================
      // Step 9: Create a feature branch
      // ============================================
      console.log('\n🌿 Step 9: Creating feature branch in source')
      const featureBranchName = `feature/test-${timestamp}`

      const featureBranch = await sourceGitLab.createBranch(
        sourceProject.id,
        featureBranchName,
        defaultBranch
      )
      console.log(`✅ Feature branch created: ${featureBranch.name}`)
      console.log(`   From: ${defaultBranch}`)
      console.log(`   Commit: ${featureBranch.commit.short_id}`)

      // ============================================
      // Step 10: Add commit to feature branch
      // ============================================
      console.log('\n📝 Step 10: Adding commit to feature branch')
      const featureCommitMessage = `feat: new feature work - ${new Date().toISOString()}`

      const featureCommit = await sourceGitLab.createCommit(
        sourceProject.id,
        featureBranchName,
        featureCommitMessage
      )
      console.log(`✅ Feature commit created: ${featureCommit.short_id}`)
      console.log(`   Message: ${featureCommitMessage}`)

      // ============================================
      // Step 11: Sync and verify branch synced
      // ============================================
      console.log('\n🔄 Step 11: Triggering sync for feature branch')
      await mirrorApi.triggerSync(mirrorProject!.id)
      const syncResult4 = await mirrorApi.waitForSync(mirrorProject!.id, 60000)
      console.log(`✅ Sync completed: ${syncResult4.syncStatus}`)

      console.log('   Verifying feature branch in target...')
      const targetFeatureBranch = await targetGitLab.getBranch(
        targetProject!.id,
        featureBranchName
      )
      expect(targetFeatureBranch).not.toBeNull()
      expect(targetFeatureBranch!.commit.id).toBe(featureCommit.id)
      console.log(`✅ Feature branch synced: ${targetFeatureBranch!.name}`)
      console.log(`   Commit: ${targetFeatureBranch!.commit.short_id}`)

      // ============================================
      // Step 12: Verify branch comparison
      // ============================================
      console.log('\n🔍 Step 12: Verifying branch comparison')
      const comparison = await mirrorApi.getBranchComparison(mirrorProject!.id)
      expect(comparison).not.toBeNull()

      // Check main branch
      const mainBranch = comparison!.branches.find(b => b.branchName === defaultBranch)
      expect(mainBranch).toBeDefined()
      expect(mainBranch!.syncStatus).toBe('synced')
      expect(mainBranch!.sourceCommitId).toBe(commit2.id)
      expect(mainBranch!.targetCommitId).toBe(commit2.id)
      console.log(`✅ Main branch comparison verified`)
      console.log(`   Status: ${mainBranch!.syncStatus}`)
      console.log(`   Source commit: ${mainBranch!.sourceCommitId?.substring(0, 8)}`)
      console.log(`   Target commit: ${mainBranch!.targetCommitId?.substring(0, 8)}`)

      // Check feature branch
      const featureBranchComparison = comparison!.branches.find(b => b.branchName === featureBranchName)
      expect(featureBranchComparison).toBeDefined()
      expect(featureBranchComparison!.syncStatus).toBe('synced')
      expect(featureBranchComparison!.sourceCommitId).toBe(featureCommit.id)
      expect(featureBranchComparison!.targetCommitId).toBe(featureCommit.id)
      console.log(`✅ Feature branch comparison verified`)
      console.log(`   Status: ${featureBranchComparison!.syncStatus}`)
      console.log(`   Source commit: ${featureBranchComparison!.sourceCommitId?.substring(0, 8)}`)
      console.log(`   Target commit: ${featureBranchComparison!.targetCommitId?.substring(0, 8)}`)

      // ============================================
      // Final Summary
      // ============================================
      console.log('\n✅ ===== FULL LIFECYCLE TEST PASSED =====')
      console.log('Summary:')
      console.log(`  • Project: ${projectPath}`)
      console.log(`  • Commits synced: 3 (2 on main, 1 on feature)`)
      console.log(`  • Branches synced: 2 (${defaultBranch}, ${featureBranchName})`)
      console.log(`  • All operations verified successfully`)
      console.log('==========================================\n')

    } catch (error) {
      console.error(`\n❌ Test failed: ${error}`)
      throw error
    }
  })
})
