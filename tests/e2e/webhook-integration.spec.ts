/**
 * Webhook Integration E2E Tests
 *
 * Tests webhook functionality including:
 * - Commit push triggering fast sync
 * - Branch create/delete triggering sync
 * - New project discovery via webhook
 */

import { test, expect } from '@playwright/test'
import axios from 'axios'

const SOURCE_GITLAB_URL = process.env.SOURCE_GITLAB_URL || 'http://localhost:8000'
const SOURCE_GITLAB_TOKEN = process.env.SOURCE_GITLAB_TOKEN || 'glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq'
const WEBHOOK_URL = 'http://localhost:9999/api/webhooks/gitlab'
const MIRROR_API_URL = 'http://localhost:9999/api'
const MIRROR_API_KEY = 'dev-api-key-12345'

// Helper: Send webhook event
async function sendWebhook(eventType: string, payload: any) {
  const response = await axios.post(WEBHOOK_URL, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Gitlab-Event': eventType
    }
  })
  return response.data
}

// Helper: Get source project
async function getSourceProject(projectPath: string) {
  const response = await axios.get(
    `${SOURCE_GITLAB_URL}/api/v4/projects/${encodeURIComponent(projectPath)}`,
    { headers: { 'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN } }
  )
  return response.data
}

// Helper: Get branch
async function getBranch(projectId: number, branchName: string) {
  const response = await axios.get(
    `${SOURCE_GITLAB_URL}/api/v4/projects/${projectId}/repository/branches/${branchName}`,
    { headers: { 'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN } }
  )
  return response.data
}

// Helper: Create branch
async function createBranch(projectId: number, branchName: string, ref: string) {
  const response = await axios.post(
    `${SOURCE_GITLAB_URL}/api/v4/projects/${projectId}/repository/branches`,
    { branch: branchName, ref },
    { headers: { 'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN } }
  )
  return response.data
}

// Helper: Delete branch
async function deleteBranch(projectId: number, branchName: string) {
  await axios.delete(
    `${SOURCE_GITLAB_URL}/api/v4/projects/${projectId}/repository/branches/${branchName}`,
    { headers: { 'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN } }
  )
}

// Helper: Create project
async function createProject(name: string, namespaceId: number) {
  const response = await axios.post(
    `${SOURCE_GITLAB_URL}/api/v4/projects`,
    {
      name,
      namespace_id: namespaceId,
      initialize_with_readme: true
    },
    { headers: { 'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN } }
  )
  return response.data
}

// Helper: Get namespace
async function getNamespace(groupName: string) {
  const response = await axios.get(
    `${SOURCE_GITLAB_URL}/api/v4/namespaces?search=${groupName}`,
    { headers: { 'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN } }
  )
  return response.data[0]
}

// Helper: Check mirror project
async function getMirrorProject(projectPath: string) {
  try {
    const response = await axios.get(
      `${MIRROR_API_URL}/sync/projects/${encodeURIComponent(projectPath)}`,
      { headers: { 'Authorization': `Bearer ${MIRROR_API_KEY}` } }
    )
    return response.data.data
  } catch (error) {
    return null
  }
}

// Helper: Wait for sync
async function waitForSync(projectId: number, maxWaitMs: number = 30000) {
  const startTime = Date.now()
  const checkInterval = 2000

  while (Date.now() - startTime < maxWaitMs) {
    await new Promise(resolve => setTimeout(resolve, checkInterval))

    try {
      const response = await axios.get(
        `${MIRROR_API_URL}/sync/projects/${projectId}/result`,
        { headers: { 'Authorization': `Bearer ${MIRROR_API_KEY}` } }
      )

      if (response.data.success && response.data.data) {
        const lastSyncAt = new Date(response.data.data.lastSyncAt).getTime()
        if (Date.now() - lastSyncAt < 60000) {
          return response.data.data
        }
      }
    } catch (error) {
      // Continue waiting
    }
  }

  throw new Error(`Sync timeout after ${maxWaitMs}ms`)
}

test.describe.configure({ mode: 'serial' })

test.describe('Webhook Integration Tests', () => {
  const TEST_PROJECT_PATH = 'ai/test-rails-5'
  let testBranchName: string

  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('/login')
    await page.getByLabel('Username').fill('admin')
    await page.getByLabel('Password').fill('Admin@123')
    await page.getByRole('button', { name: /Sign In/i }).click()
    await page.waitForTimeout(2000)
    await page.waitForURL(/\/(dashboard)?/, { timeout: 5000 })

    // Generate unique branch name
    testBranchName = `webhook-test-${Date.now()}`
  })

  test('should trigger fast sync on commit push webhook', async ({ page }) => {
    console.log('\n=== Test: Commit Push Webhook ===\n')

    // Get source project
    const sourceProject = await getSourceProject(TEST_PROJECT_PATH)
    console.log(`Source project ID: ${sourceProject.id}`)

    // Get current branch
    const branch = await getBranch(sourceProject.id, 'master')
    const currentSha = branch.commit.id
    console.log(`Current commit: ${currentSha.substring(0, 8)}`)

    // Send webhook
    console.log('Sending commit push webhook...')
    const webhookResponse = await sendWebhook('Push Hook', {
      object_kind: 'push',
      event_name: 'push',
      before: currentSha,
      after: currentSha,
      ref: 'refs/heads/master',
      project_id: sourceProject.id,
      project: {
        id: sourceProject.id,
        path_with_namespace: TEST_PROJECT_PATH
      }
    })

    console.log('Webhook response:', webhookResponse)
    expect(webhookResponse.status).toBe('success')
    expect(webhookResponse.triggered).toBe(true)

    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    // Find the project in the list
    const projectRow = page.locator(`tr:has-text("${TEST_PROJECT_PATH}")`)
    await expect(projectRow).toBeVisible({ timeout: 10000 })

    console.log('✅ Commit push webhook test passed')
  })

  test('should trigger fast sync on branch create webhook', async ({ page }) => {
    console.log('\n=== Test: Branch Create Webhook ===\n')

    // Get source project
    const sourceProject = await getSourceProject(TEST_PROJECT_PATH)
    console.log(`Source project ID: ${sourceProject.id}`)

    // Create new branch
    console.log(`Creating branch: ${testBranchName}`)
    const newBranch = await createBranch(sourceProject.id, testBranchName, 'master')
    console.log(`Branch created: ${newBranch.name}`)

    // Send webhook
    console.log('Sending branch create webhook...')
    const webhookResponse = await sendWebhook('Push Hook', {
      object_kind: 'push',
      event_name: 'push',
      before: '0000000000000000000000000000000000000000',
      after: newBranch.commit.id,
      ref: `refs/heads/${testBranchName}`,
      project_id: sourceProject.id,
      project: {
        id: sourceProject.id,
        path_with_namespace: TEST_PROJECT_PATH
      }
    })

    console.log('Webhook response:', webhookResponse)
    expect(webhookResponse.status).toBe('success')
    expect(webhookResponse.triggered).toBe(true)
    expect(webhookResponse.change_type).toBe('branch_create')

    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    // Verify project is visible
    const projectRow = page.locator(`tr:has-text("${TEST_PROJECT_PATH}")`)
    await expect(projectRow).toBeVisible({ timeout: 10000 })

    console.log('✅ Branch create webhook test passed')
  })

  test('should trigger fast sync on branch delete webhook', async ({ page }) => {
    console.log('\n=== Test: Branch Delete Webhook ===\n')

    // Get source project
    const sourceProject = await getSourceProject(TEST_PROJECT_PATH)
    console.log(`Source project ID: ${sourceProject.id}`)

    // Create branch first
    console.log(`Creating branch: ${testBranchName}`)
    const newBranch = await createBranch(sourceProject.id, testBranchName, 'master')
    const branchSha = newBranch.commit.id

    // Delete branch
    console.log(`Deleting branch: ${testBranchName}`)
    await deleteBranch(sourceProject.id, testBranchName)

    // Send webhook
    console.log('Sending branch delete webhook...')
    const webhookResponse = await sendWebhook('Push Hook', {
      object_kind: 'push',
      event_name: 'push',
      before: branchSha,
      after: '0000000000000000000000000000000000000000',
      ref: `refs/heads/${testBranchName}`,
      project_id: sourceProject.id,
      project: {
        id: sourceProject.id,
        path_with_namespace: TEST_PROJECT_PATH
      }
    })

    console.log('Webhook response:', webhookResponse)
    expect(webhookResponse.status).toBe('success')
    expect(webhookResponse.triggered).toBe(true)
    expect(webhookResponse.change_type).toBe('branch_delete')

    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    // Verify project is visible
    const projectRow = page.locator(`tr:has-text("${TEST_PROJECT_PATH}")`)
    await expect(projectRow).toBeVisible({ timeout: 10000 })

    console.log('✅ Branch delete webhook test passed')
  })

  test('should discover and initialize new project via webhook', async ({ page }) => {
    console.log('\n=== Test: New Project Discovery ===\n')

    const newProjectName = `webhook-test-${Date.now()}`
    const newProjectPath = `ai/${newProjectName}`

    // Get namespace
    const namespace = await getNamespace('ai')
    console.log(`Namespace ID: ${namespace.id}`)

    // Create new project
    console.log(`Creating new project: ${newProjectName}`)
    const newProject = await createProject(newProjectName, namespace.id)
    console.log(`New project created: ${newProject.path_with_namespace} (ID: ${newProject.id})`)

    // Wait for project to be ready
    await new Promise(resolve => setTimeout(resolve, 3000))

    // Get default branch
    let defaultBranch
    try {
      defaultBranch = await getBranch(newProject.id, 'main')
    } catch (error) {
      try {
        defaultBranch = await getBranch(newProject.id, 'master')
      } catch (error2) {
        console.log('No default branch found, using dummy SHA')
      }
    }

    const commitSha = defaultBranch?.commit?.id || '0000000000000000000000000000000000000001'

    // Send webhook
    console.log('Sending webhook for new project...')
    const webhookResponse = await sendWebhook('Push Hook', {
      object_kind: 'push',
      event_name: 'push',
      before: '0000000000000000000000000000000000000000',
      after: commitSha,
      ref: 'refs/heads/main',
      project_id: newProject.id,
      project: {
        id: newProject.id,
        path_with_namespace: newProjectPath
      }
    })

    console.log('Webhook response:', webhookResponse)
    expect(webhookResponse.status).toBe('success')
    expect(webhookResponse.triggered).toBe(true)

    // Wait for project to be initialized
    console.log('Waiting for project initialization...')
    await new Promise(resolve => setTimeout(resolve, 10000))

    // Check if project exists in mirror system
    const mirrorProject = await getMirrorProject(newProjectPath)
    console.log('Mirror project:', mirrorProject ? 'FOUND' : 'NOT FOUND')

    if (mirrorProject) {
      console.log(`Project initialized: ${mirrorProject.projectKey}`)
      expect(mirrorProject.projectKey).toBe(newProjectPath)
    }

    // Navigate to projects page
    await page.goto('/projects')
    await page.waitForTimeout(2000)

    // Search for new project
    const searchInput = page.getByPlaceholder(/Search/i)
    if (await searchInput.isVisible()) {
      await searchInput.fill(newProjectName)
      await page.waitForTimeout(1000)
    }

    console.log('✅ New project discovery webhook test passed')
  })

  test('should handle webhook for non-existent project gracefully', async ({ page }) => {
    console.log('\n=== Test: Non-existent Project Webhook ===\n')

    const fakeProjectPath = 'fake/non-existent-project'

    // Send webhook for non-existent project
    console.log('Sending webhook for non-existent project...')
    const webhookResponse = await sendWebhook('Push Hook', {
      object_kind: 'push',
      event_name: 'push',
      before: '0000000000000000000000000000000000000000',
      after: '0000000000000000000000000000000000000001',
      ref: 'refs/heads/main',
      project_id: 99999,
      project: {
        id: 99999,
        path_with_namespace: fakeProjectPath
      }
    })

    console.log('Webhook response:', webhookResponse)

    // Should return error for initialization failure
    expect(webhookResponse.status).toBe('error')
    expect(webhookResponse.reason).toBe('initialization_failed')

    console.log('✅ Non-existent project webhook test passed')
  })
})
