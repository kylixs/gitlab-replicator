import { ref, watch } from 'vue'

// Global state for auto-refresh settings
const isAutoRefreshEnabled = ref<boolean>(true)
const refreshInterval = ref<number>(30) // seconds

// Load settings from localStorage
const loadSettings = () => {
  const savedEnabled = localStorage.getItem('autoRefreshEnabled')
  const savedInterval = localStorage.getItem('refreshInterval')

  if (savedEnabled !== null) {
    isAutoRefreshEnabled.value = savedEnabled === 'true'
  }
  if (savedInterval !== null) {
    refreshInterval.value = parseInt(savedInterval, 10)
  }
}

// Initialize settings
loadSettings()

// Watch for changes and save to localStorage
watch(isAutoRefreshEnabled, (value) => {
  localStorage.setItem('autoRefreshEnabled', value.toString())
})

watch(refreshInterval, (value) => {
  localStorage.setItem('refreshInterval', value.toString())
})

/**
 * Auto-refresh composable for managing page refresh settings
 */
export function useAutoRefresh() {
  return {
    isAutoRefreshEnabled,
    refreshInterval
  }
}

/**
 * Hook to manage auto-refresh timer for a specific callback
 */
export function useAutoRefreshTimer(callback: () => void) {
  let timer: number | null = null

  const startTimer = () => {
    stopTimer()
    if (isAutoRefreshEnabled.value) {
      timer = window.setInterval(() => {
        callback()
      }, refreshInterval.value * 1000)
    }
  }

  const stopTimer = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  // Watch for settings changes and restart timer
  watch([isAutoRefreshEnabled, refreshInterval], () => {
    startTimer()
  })

  return {
    startTimer,
    stopTimer
  }
}
