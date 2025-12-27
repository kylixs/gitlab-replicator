/**
 * Quick Test for Long-Running Simulation
 *
 * Runs a short version (5 minutes) to verify all scenarios work
 */

import { LongRunningSimulation, CONFIG } from './long-running-simulation'

// Override config for quick test
const QUICK_CONFIG = {
  ...CONFIG,
  MAX_DURATION_HOURS: 0.1, // 6 minutes

  // Faster intervals
  COMMIT_INTERVAL_MIN: 0.5,
  COMMIT_INTERVAL_MAX: 1,

  BRANCH_INTERVAL_MIN: 1,
  BRANCH_INTERVAL_MAX: 2,

  TAG_INTERVAL_MIN: 1.5,
  TAG_INTERVAL_MAX: 2.5,

  MR_INTERVAL_MIN: 2,
  MR_INTERVAL_MAX: 3,

  CLEANUP_INTERVAL_MIN: 3,
  CLEANUP_INTERVAL_MAX: 4,

  // Shorter wait for cleanup (for testing)
  MIN_HOURS_BEFORE_DELETE: 0.05, // 3 minutes

  MAX_OPEN_BRANCHES: 5,
  MAX_MERGED_BRANCHES: 3,
}

async function runQuickTest() {
  console.log('='.repeat(80))
  console.log('QUICK TEST MODE - Running for ~6 minutes')
  console.log('='.repeat(80))
  console.log('')

  // Override config
  Object.assign(CONFIG, QUICK_CONFIG)

  const simulation = new LongRunningSimulation()

  // Handle graceful shutdown
  process.on('SIGINT', () => {
    console.log('\nReceived SIGINT, shutting down...')
    simulation.stop()
    process.exit(0)
  })

  try {
    await simulation.start()

    // Wait for the test duration
    const duration = CONFIG.MAX_DURATION_HOURS * 60 * 60 * 1000
    await new Promise(resolve => setTimeout(resolve, duration))

    simulation.stop()

    console.log('')
    console.log('='.repeat(80))
    console.log('QUICK TEST COMPLETED')
    console.log('='.repeat(80))

  } catch (error) {
    console.error('Quick test failed:', error)
    process.exit(1)
  }
}

runQuickTest()
