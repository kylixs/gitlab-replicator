import { test, expect } from '@playwright/test'
import { sourceGitLab, targetGitLab } from './helpers/gitlab-helper'
import { mirrorApi } from './helpers/mirror-api-helper'

/**
 * Integration Test: New Project Sync
 *
 * Tests that new projects created in source GitLab are automatically discovered and synced
 */

test.describe('New Project Sync Integration Tests', () => {
  const testGroups = ['ai', 'arch']

  for (const groupName of testGroups) {
    test(`should discover and sync new project in ${groupName} group`, async () => {
      console.log(`\n=== Testing new project sync for ${groupName} group ===`)

      // Step 1: Create a new project in source GitLab
      const timestamp = Date.now()
      const projectName = `test-project-${timestamp}`
      console.log(`Creating project: ${groupName}/${projectName}`)

      const sourceProject = await sourceGitLab.createProject(
        groupName,
        projectName,
        `Integration test project created at ${new Date().toISOString()}`
      )
      console.log(`Source project created: ${sourceProject.path_with_namespace}`)
      console.log(`Project ID: ${sourceProject.id}`)

      try {
        // Step 2: Wait for project discovery (scheduled task runs every 5 minutes)
        console.log('Waiting for project discovery (max 6 minutes)...')
        let mirrorProject = null
        const maxWaitTime = 360000 // 6 minutes
        const checkInterval = 10000 // 10 seconds
        const startTime = Date.now()

        while (Date.now() - startTime < maxWaitTime) {
          mirrorProject = await mirrorApi.getProject(sourceProject.path_with_namespace)
          if (mirrorProject) {
            console.log(`✅ Project discovered: ${mirrorProject.projectKey}`)
            break
          }
          await new Promise(resolve => setTimeout(resolve, checkInterval))
        }

        expect(mirrorProject).not.toBeNull()
        console.log(`Mirror project ID: ${mirrorProject!.id}`)
        console.log(`Sync status: ${mirrorProject!.syncStatus}`)

        // Step 3: Wait for initial sync to complete
        console.log('Waiting for initial sync to complete...')
        const syncResult = await mirrorApi.waitForSync(mirrorProject!.id, 120000)
        console.log(`Initial sync completed with status: ${syncResult.syncStatus}`)

        // Step 4: Verify sync result
        expect(['success', 'skipped']).toContain(syncResult.syncStatus)

        // Step 5: Verify target GitLab has the project
        const targetProject = await targetGitLab.getProject(sourceProject.path_with_namespace)
        expect(targetProject).not.toBeNull()
        console.log(`✅ Target project created: ${targetProject!.path_with_namespace}`)

        // Step 6: Verify branches are synced
        const sourceBranches = await sourceGitLab.getBranches(sourceProject.id)
        const targetBranches = await targetGitLab.getBranches(targetProject!.id)

        console.log(`Source branches: ${sourceBranches.length}`)
        console.log(`Target branches: ${targetBranches.length}`)
        expect(targetBranches.length).toBe(sourceBranches.length)

        // Step 7: Verify branch comparison
        const comparison = await mirrorApi.getBranchComparison(mirrorProject!.id)
        expect(comparison).not.toBeNull()
        expect(comparison!.syncedCount).toBe(sourceBranches.length)
        expect(comparison!.outdatedCount).toBe(0)
        expect(comparison!.missingInTargetCount).toBe(0)
        console.log(`✅ Branch comparison verified: ${comparison!.syncedCount} branches synced`)

        // Step 8: Test commit sync on new project
        console.log('Testing commit sync on new project...')
        const branchName = sourceProject.default_branch || 'main'
        const commitMessage = `test: commit on new project at ${new Date().toISOString()}`

        const newCommit = await sourceGitLab.createCommit(
          sourceProject.id,
          branchName,
          commitMessage
        )
        console.log(`New commit created: ${newCommit.short_id}`)

        // Trigger sync
        await mirrorApi.triggerSync(mirrorProject!.id)
        const syncResult2 = await mirrorApi.waitForSync(mirrorProject!.id, 60000)
        console.log(`Sync completed with status: ${syncResult2.syncStatus}`)

        // Verify target has the new commit
        const targetBranch = await targetGitLab.getBranch(targetProject!.id, branchName)
        expect(targetBranch).not.toBeNull()
        expect(targetBranch!.commit.id).toBe(newCommit.id)
        console.log(`✅ New commit synced to target: ${targetBranch!.commit.short_id}`)

        console.log(`\n✅ New project sync test passed for ${groupName} group\n`)

      } finally {
        // Cleanup: Delete the test project from both source and target
        console.log(`Cleaning up test project: ${sourceProject.path_with_namespace}`)
        await sourceGitLab.deleteProject(sourceProject.id)

        const targetProject = await targetGitLab.getProject(sourceProject.path_with_namespace)
        if (targetProject) {
          await targetGitLab.deleteProject(targetProject.id)
        }
        console.log('✅ Cleanup completed')
      }
    })
  }
})

test.describe('Project Discovery Tests', () => {
  test('should list all projects in ai and arch groups', async () => {
    console.log('\n=== Testing project discovery ===')

    // Test ai group
    const aiProjects = await mirrorApi.getProjects({ group: 'ai' })
    console.log(`AI group projects: ${aiProjects.total}`)
    expect(aiProjects.items.length).toBeGreaterThan(0)

    for (const project of aiProjects.items) {
      console.log(`  - ${project.projectKey} (${project.syncStatus})`)
      expect(project.projectKey).toContain('ai/')
    }

    // Test arch group
    const archProjects = await mirrorApi.getProjects({ group: 'arch' })
    console.log(`Arch group projects: ${archProjects.total}`)
    expect(archProjects.items.length).toBeGreaterThan(0)

    for (const project of archProjects.items) {
      console.log(`  - ${project.projectKey} (${project.syncStatus})`)
      expect(project.projectKey).toContain('arch/')
    }

    console.log('\n✅ Project discovery test passed\n')
  })

  test('should verify all projects have valid sync status', async () => {
    console.log('\n=== Testing project sync status ===')

    const allProjects = await mirrorApi.getProjects()
    console.log(`Total projects: ${allProjects.total}`)

    const validStatuses = ['active', 'synced', 'outdated', 'paused', 'failed', 'pending', 'discovered', 'initializing', 'missing']

    for (const project of allProjects.items) {
      expect(validStatuses).toContain(project.syncStatus)
      console.log(`  ${project.projectKey}: ${project.syncStatus}`)

      // Verify project has sync result
      const syncResult = await mirrorApi.getSyncResult(project.id)
      if (syncResult) {
        expect(['success', 'failed', 'skipped']).toContain(syncResult.syncStatus)
        console.log(`    Last sync: ${syncResult.syncStatus} at ${syncResult.lastSyncAt}`)
      }
    }

    console.log('\n✅ Project sync status test passed\n')
  })
})
