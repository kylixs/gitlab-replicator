/**
 * Long-Running E2E Simulation Test
 *
 * Simulates real user operations over a 3-day period:
 * - Commit code changes
 * - Create feature branches
 * - Create tags
 * - Create and merge MRs
 * - Delete merged branches
 *
 * Frequency control: Operations spread out over time to simulate realistic usage
 * Duration: Up to 3 days (72 hours)
 */

import { GitLabHelper, GitLabProject, GitLabBranch } from '../integration/helpers/gitlab-helper'

// Configuration
const SOURCE_GITLAB_URL = process.env.SOURCE_GITLAB_URL || 'http://localhost:8000'
const SOURCE_GITLAB_TOKEN = process.env.SOURCE_GITLAB_TOKEN || 'glpat-QfaqawuLrzcPfJ3oEgiStG86MQp1OjEH.01.0w01363lq'

const CONFIG = {
  // Test duration
  MAX_DURATION_HOURS: 72, // 3 days

  // Project configuration
  GROUP_PATH: 'test-group',
  PROJECT_NAME: 'long-running-test',

  // Operation frequencies (minutes)
  COMMIT_INTERVAL_MIN: 15,
  COMMIT_INTERVAL_MAX: 45,

  BRANCH_INTERVAL_MIN: 60,
  BRANCH_INTERVAL_MAX: 180,

  TAG_INTERVAL_MIN: 120,
  TAG_INTERVAL_MAX: 360,

  MR_INTERVAL_MIN: 90,
  MR_INTERVAL_MAX: 240,

  CLEANUP_INTERVAL_MIN: 180,
  CLEANUP_INTERVAL_MAX: 360,

  // Cleanup rules
  MIN_HOURS_BEFORE_DELETE: 2, // Wait at least 2 hours before deleting merged branches

  // Limits
  MAX_OPEN_BRANCHES: 10,
  MAX_MERGED_BRANCHES: 5,
}

interface MergedBranch {
  name: string
  mergedAt: number
}

interface TestState {
  project: GitLabProject | null
  startTime: number
  operationCount: number
  branches: string[]
  mergedBranches: MergedBranch[]
  tags: string[]
  mergeRequests: number[]
  errors: Array<{ time: number, operation: string, error: string }>
}

class LongRunningSimulation {
  private gitlab: GitLabHelper
  private state: TestState
  private isRunning: boolean = false
  private timers: NodeJS.Timeout[] = []

  constructor() {
    this.gitlab = new GitLabHelper(true)
    this.state = {
      project: null,
      startTime: Date.now(),
      operationCount: 0,
      branches: [],
      mergedBranches: [],
      tags: [],
      mergeRequests: [],
      errors: []
    }
  }

  /**
   * Log with timestamp
   */
  private log(message: string, level: 'INFO' | 'WARN' | 'ERROR' = 'INFO') {
    const timestamp = new Date().toISOString()
    const elapsed = Math.round((Date.now() - this.state.startTime) / 1000 / 60)
    console.log(`[${timestamp}] [${level}] [Elapsed: ${elapsed}m] ${message}`)
  }

  /**
   * Random delay between min and max minutes
   */
  private randomDelay(minMinutes: number, maxMinutes: number): number {
    return (minMinutes + Math.random() * (maxMinutes - minMinutes)) * 60 * 1000
  }

  /**
   * Sleep for specified milliseconds
   */
  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  /**
   * Check if we should continue running
   */
  private shouldContinue(): boolean {
    const elapsed = (Date.now() - this.state.startTime) / 1000 / 60 / 60
    return this.isRunning && elapsed < CONFIG.MAX_DURATION_HOURS
  }

  /**
   * Initialize test project
   */
  async initialize(): Promise<void> {
    this.log('Initializing test project...')

    try {
      // Check if project already exists
      const projectPath = `${CONFIG.GROUP_PATH}/${CONFIG.PROJECT_NAME}`
      let project = await this.gitlab.getProject(projectPath)

      if (!project) {
        this.log('Creating new test project...')
        project = await this.gitlab.createProject(
          CONFIG.GROUP_PATH,
          CONFIG.PROJECT_NAME,
          'Long-running E2E test project'
        )
      }

      this.state.project = project
      this.log(`Project initialized: ${project.path_with_namespace} (ID: ${project.id})`)
    } catch (error) {
      this.log(`Failed to initialize project: ${error}`, 'ERROR')
      throw error
    }
  }

  /**
   * Scenario 1: Commit code changes
   */
  async commitCodeChanges(): Promise<void> {
    if (!this.state.project || !this.shouldContinue()) return

    try {
      const branch = this.state.branches.length > 0
        ? this.state.branches[Math.floor(Math.random() * this.state.branches.length)]
        : this.state.project.default_branch

      const fileIndex = this.state.operationCount
      const commit = await this.gitlab.createCommit(
        this.state.project.id,
        branch,
        `feat: add feature ${fileIndex}`,
        `src/feature_${fileIndex}.ts`
      )

      this.log(`✓ Created commit ${commit.short_id} on branch '${branch}'`)
      this.state.operationCount++
    } catch (error: any) {
      this.log(`✗ Failed to create commit: ${error.message}`, 'ERROR')
      this.state.errors.push({
        time: Date.now(),
        operation: 'commit',
        error: error.message
      })
    }

    // Schedule next commit
    if (this.shouldContinue()) {
      const delay = this.randomDelay(CONFIG.COMMIT_INTERVAL_MIN, CONFIG.COMMIT_INTERVAL_MAX)
      this.log(`Next commit scheduled in ${Math.round(delay / 60000)} minutes`)
      const timer = setTimeout(() => this.commitCodeChanges(), delay)
      this.timers.push(timer)
    }
  }

  /**
   * Scenario 2: Create feature branch
   */
  async createFeatureBranch(): Promise<void> {
    if (!this.state.project || !this.shouldContinue()) return

    try {
      // Don't create too many branches
      if (this.state.branches.length >= CONFIG.MAX_OPEN_BRANCHES) {
        this.log(`Skipping branch creation: ${this.state.branches.length} branches already open`, 'WARN')
        this.scheduleNextBranch()
        return
      }

      const branchName = `feature/test-${Date.now()}`
      const branch = await this.gitlab.createBranch(
        this.state.project.id,
        branchName,
        this.state.project.default_branch
      )

      this.state.branches.push(branchName)
      this.log(`✓ Created branch '${branchName}' (Total: ${this.state.branches.length})`)
      this.state.operationCount++
    } catch (error: any) {
      this.log(`✗ Failed to create branch: ${error.message}`, 'ERROR')
      this.state.errors.push({
        time: Date.now(),
        operation: 'branch',
        error: error.message
      })
    }

    this.scheduleNextBranch()
  }

  private scheduleNextBranch(): void {
    if (this.shouldContinue()) {
      const delay = this.randomDelay(CONFIG.BRANCH_INTERVAL_MIN, CONFIG.BRANCH_INTERVAL_MAX)
      this.log(`Next branch scheduled in ${Math.round(delay / 60000)} minutes`)
      const timer = setTimeout(() => this.createFeatureBranch(), delay)
      this.timers.push(timer)
    }
  }

  /**
   * Scenario 3: Create tag
   */
  async createTag(): Promise<void> {
    if (!this.state.project || !this.shouldContinue()) return

    try {
      const tagName = `v1.0.${this.state.tags.length}`
      const response = await fetch(
        `${SOURCE_GITLAB_URL}/api/v4/projects/${this.state.project.id}/repository/tags`,
        {
          method: 'POST',
          headers: {
            'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            tag_name: tagName,
            ref: this.state.project.default_branch,
            message: `Release ${tagName}`
          })
        }
      )

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${await response.text()}`)
      }

      this.state.tags.push(tagName)
      this.log(`✓ Created tag '${tagName}' (Total: ${this.state.tags.length})`)
      this.state.operationCount++
    } catch (error: any) {
      this.log(`✗ Failed to create tag: ${error.message}`, 'ERROR')
      this.state.errors.push({
        time: Date.now(),
        operation: 'tag',
        error: error.message
      })
    }

    // Schedule next tag
    if (this.shouldContinue()) {
      const delay = this.randomDelay(CONFIG.TAG_INTERVAL_MIN, CONFIG.TAG_INTERVAL_MAX)
      this.log(`Next tag scheduled in ${Math.round(delay / 60000)} minutes`)
      const timer = setTimeout(() => this.createTag(), delay)
      this.timers.push(timer)
    }
  }

  /**
   * Scenario 4: Create and merge MR
   */
  async createAndMergeMR(): Promise<void> {
    if (!this.state.project || !this.shouldContinue()) return

    try {
      // Need at least one feature branch
      if (this.state.branches.length === 0) {
        this.log('Skipping MR: No feature branches available', 'WARN')
        this.scheduleNextMR()
        return
      }

      // Pick a random feature branch
      const branchIndex = Math.floor(Math.random() * this.state.branches.length)
      const sourceBranch = this.state.branches[branchIndex]

      // Create MR
      const mrResponse = await fetch(
        `${SOURCE_GITLAB_URL}/api/v4/projects/${this.state.project.id}/merge_requests`,
        {
          method: 'POST',
          headers: {
            'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            source_branch: sourceBranch,
            target_branch: this.state.project.default_branch,
            title: `Merge ${sourceBranch} into ${this.state.project.default_branch}`,
            description: `Automated MR from long-running test`
          })
        }
      )

      if (!mrResponse.ok) {
        throw new Error(`Failed to create MR: HTTP ${mrResponse.status}`)
      }

      const mr = await mrResponse.json()
      this.log(`✓ Created MR !${mr.iid}: ${sourceBranch} → ${this.state.project.default_branch}`)

      // Wait a bit before merging
      await this.sleep(5000)

      // Merge the MR
      const mergeResponse = await fetch(
        `${SOURCE_GITLAB_URL}/api/v4/projects/${this.state.project.id}/merge_requests/${mr.iid}/merge`,
        {
          method: 'PUT',
          headers: {
            'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            should_remove_source_branch: false // Keep branch for later cleanup test
          })
        }
      )

      if (!mergeResponse.ok) {
        throw new Error(`Failed to merge MR: HTTP ${mergeResponse.status}`)
      }

      this.log(`✓ Merged MR !${mr.iid}`)

      // Move branch to merged list with timestamp
      this.state.branches.splice(branchIndex, 1)
      this.state.mergedBranches.push({
        name: sourceBranch,
        mergedAt: Date.now()
      })
      this.state.mergeRequests.push(mr.iid)
      this.state.operationCount++
    } catch (error: any) {
      this.log(`✗ Failed MR operation: ${error.message}`, 'ERROR')
      this.state.errors.push({
        time: Date.now(),
        operation: 'merge_request',
        error: error.message
      })
    }

    this.scheduleNextMR()
  }

  private scheduleNextMR(): void {
    if (this.shouldContinue()) {
      const delay = this.randomDelay(CONFIG.MR_INTERVAL_MIN, CONFIG.MR_INTERVAL_MAX)
      this.log(`Next MR scheduled in ${Math.round(delay / 60000)} minutes`)
      const timer = setTimeout(() => this.createAndMergeMR(), delay)
      this.timers.push(timer)
    }
  }

  /**
   * Scenario 5: Delete merged branches (only if merged at least 2 hours ago)
   */
  async deletemergedBranches(): Promise<void> {
    if (!this.state.project || !this.shouldContinue()) return

    try {
      if (this.state.mergedBranches.length === 0) {
        this.log('No merged branches to delete', 'WARN')
        this.scheduleNextCleanup()
        return
      }

      // Find branches that are old enough to delete (at least 2 hours old)
      const minAge = CONFIG.MIN_HOURS_BEFORE_DELETE * 60 * 60 * 1000
      const now = Date.now()
      const eligibleBranches = this.state.mergedBranches.filter(
        b => (now - b.mergedAt) >= minAge
      )

      if (eligibleBranches.length === 0) {
        const nextEligible = this.state.mergedBranches[0]
        const waitTime = Math.round((minAge - (now - nextEligible.mergedAt)) / 60000)
        this.log(`No branches old enough to delete yet. Next eligible in ${waitTime} minutes`, 'WARN')
        this.scheduleNextCleanup()
        return
      }

      this.log(`Found ${eligibleBranches.length} branches eligible for deletion (>= ${CONFIG.MIN_HOURS_BEFORE_DELETE}h old)`)

      // Delete up to 3 eligible branches at a time
      const toDelete = eligibleBranches.slice(0, Math.min(3, eligibleBranches.length))

      for (const branch of toDelete) {
        const ageHours = ((now - branch.mergedAt) / 1000 / 60 / 60).toFixed(1)
        const response = await fetch(
          `${SOURCE_GITLAB_URL}/api/v4/projects/${this.state.project.id}/repository/branches/${encodeURIComponent(branch.name)}`,
          {
            method: 'DELETE',
            headers: {
              'PRIVATE-TOKEN': SOURCE_GITLAB_TOKEN
            }
          }
        )

        if (response.ok || response.status === 404) {
          this.log(`✓ Deleted merged branch '${branch.name}' (age: ${ageHours}h)`)
          this.state.mergedBranches = this.state.mergedBranches.filter(b => b.name !== branch.name)
          this.state.operationCount++
        } else {
          this.log(`✗ Failed to delete branch '${branch.name}': HTTP ${response.status}`, 'WARN')
        }
      }
    } catch (error: any) {
      this.log(`✗ Failed to delete branches: ${error.message}`, 'ERROR')
      this.state.errors.push({
        time: Date.now(),
        operation: 'cleanup',
        error: error.message
      })
    }

    this.scheduleNextCleanup()
  }

  private scheduleNextCleanup(): void {
    if (this.shouldContinue()) {
      const delay = this.randomDelay(CONFIG.CLEANUP_INTERVAL_MIN, CONFIG.CLEANUP_INTERVAL_MAX)
      this.log(`Next cleanup scheduled in ${Math.round(delay / 60000)} minutes`)
      const timer = setTimeout(() => this.deletemergedBranches(), delay)
      this.timers.push(timer)
    }
  }

  /**
   * Print status summary
   */
  private printStatus(): void {
    const elapsed = Math.round((Date.now() - this.state.startTime) / 1000 / 60)
    const elapsedHours = (elapsed / 60).toFixed(1)

    console.log('\n' + '='.repeat(80))
    console.log(`STATUS SUMMARY - Elapsed: ${elapsedHours}h (${elapsed}m)`)
    console.log('='.repeat(80))
    console.log(`Total Operations: ${this.state.operationCount}`)
    console.log(`Open Branches: ${this.state.branches.length}`)
    console.log(`Merged Branches: ${this.state.mergedBranches.length}`)
    console.log(`Tags Created: ${this.state.tags.length}`)
    console.log(`Merge Requests: ${this.state.mergeRequests.length}`)
    console.log(`Errors: ${this.state.errors.length}`)

    if (this.state.errors.length > 0) {
      console.log('\nRecent Errors:')
      this.state.errors.slice(-5).forEach(e => {
        const errorTime = new Date(e.time).toISOString()
        console.log(`  [${errorTime}] ${e.operation}: ${e.error}`)
      })
    }
    console.log('='.repeat(80) + '\n')
  }

  /**
   * Start the simulation
   */
  async start(): Promise<void> {
    this.log('Starting long-running E2E simulation...')
    this.log(`Duration: ${CONFIG.MAX_DURATION_HOURS} hours`)
    this.log(`Project: ${CONFIG.GROUP_PATH}/${CONFIG.PROJECT_NAME}`)

    try {
      await this.initialize()
      this.isRunning = true

      // Start all scenarios
      this.log('Starting operation scenarios...')

      // Stagger the initial start times
      setTimeout(() => this.commitCodeChanges(), 1000)
      setTimeout(() => this.createFeatureBranch(), 5000)
      setTimeout(() => this.createTag(), 10000)
      setTimeout(() => this.createAndMergeMR(), 15000)
      setTimeout(() => this.deletemergedBranches(), 20000)

      // Print status every 30 minutes
      const statusTimer = setInterval(() => {
        if (!this.shouldContinue()) {
          clearInterval(statusTimer)
          return
        }
        this.printStatus()
      }, 30 * 60 * 1000)

      this.log('All scenarios started. Simulation is running...')
      this.printStatus()

    } catch (error) {
      this.log(`Failed to start simulation: ${error}`, 'ERROR')
      throw error
    }
  }

  /**
   * Stop the simulation
   */
  stop(): void {
    this.log('Stopping simulation...')
    this.isRunning = false

    // Clear all timers
    this.timers.forEach(timer => clearTimeout(timer))
    this.timers = []

    this.printStatus()
    this.log('Simulation stopped.')
  }
}

// Main execution
if (require.main === module) {
  const simulation = new LongRunningSimulation()

  // Handle graceful shutdown
  process.on('SIGINT', () => {
    console.log('\nReceived SIGINT, shutting down gracefully...')
    simulation.stop()
    process.exit(0)
  })

  process.on('SIGTERM', () => {
    console.log('\nReceived SIGTERM, shutting down gracefully...')
    simulation.stop()
    process.exit(0)
  })

  // Start simulation
  simulation.start().catch(error => {
    console.error('Fatal error:', error)
    process.exit(1)
  })
}

export { LongRunningSimulation, CONFIG }
