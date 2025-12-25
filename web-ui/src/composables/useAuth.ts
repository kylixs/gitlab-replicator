import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, verifyToken, AuthApiError } from '@/api/auth'
import type { UserInfo } from '@/api/types/auth'

// Global state
const isAuthenticated = ref(false)
const currentUser = ref<UserInfo | null>(null)
const failureCount = ref(0)
const lockoutSeconds = ref(0)
const isLoading = ref(false)
const error = ref<string | null>(null)

// Lockout countdown timer
let lockoutTimer: number | null = null

/**
 * Start lockout countdown
 */
function startLockoutCountdown(seconds: number) {
  lockoutSeconds.value = seconds

  // Clear existing timer
  if (lockoutTimer) {
    clearInterval(lockoutTimer)
  }

  // Start countdown
  lockoutTimer = setInterval(() => {
    lockoutSeconds.value--

    if (lockoutSeconds.value <= 0) {
      if (lockoutTimer) {
        clearInterval(lockoutTimer)
        lockoutTimer = null
      }
      failureCount.value = 0 // Reset failure count when lockout expires
    }
  }, 1000) as unknown as number
}

/**
 * Authentication composable
 */
export function useAuth() {
  /**
   * Login with username and password
   */
  async function login(username: string, password: string): Promise<boolean> {
    isLoading.value = true
    error.value = null

    try {
      const response = await apiLogin(username, password)

      // Update state on success
      isAuthenticated.value = true
      currentUser.value = response.user
      failureCount.value = 0
      lockoutSeconds.value = 0

      if (lockoutTimer) {
        clearInterval(lockoutTimer)
        lockoutTimer = null
      }

      return true
    } catch (err) {
      if (err instanceof AuthApiError) {
        error.value = err.message

        // Handle account locked
        if (err.code === 'ACCOUNT_LOCKED') {
          if (err.retryAfter) {
            startLockoutCountdown(err.retryAfter)
          }
          if (err.failedAttempts) {
            failureCount.value = err.failedAttempts
          }
        } else {
          // Increment local failure count for UI feedback
          failureCount.value++
        }
      } else {
        error.value = err instanceof Error ? err.message : 'Login failed'
      }

      isAuthenticated.value = false
      currentUser.value = null
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Logout
   */
  async function logout() {
    isLoading.value = true

    try {
      await apiLogout()
    } catch (err) {
      // Ignore logout errors
      console.warn('Logout error:', err)
    } finally {
      // Always clear local state
      isAuthenticated.value = false
      currentUser.value = null
      failureCount.value = 0
      lockoutSeconds.value = 0
      error.value = null

      if (lockoutTimer) {
        clearInterval(lockoutTimer)
        lockoutTimer = null
      }

      isLoading.value = false
    }
  }

  /**
   * Check authentication status
   */
  async function checkAuth(): Promise<boolean> {
    // Quick check: if no token, not authenticated
    const token = localStorage.getItem('auth_token')
    if (!token) {
      isAuthenticated.value = false
      currentUser.value = null
      return false
    }

    // Verify token with server
    try {
      const response = await verifyToken()

      if (response.valid && response.user) {
        isAuthenticated.value = true
        currentUser.value = response.user
        return true
      } else {
        isAuthenticated.value = false
        currentUser.value = null
        return false
      }
    } catch (err) {
      // Token invalid or expired
      isAuthenticated.value = false
      currentUser.value = null
      localStorage.removeItem('auth_token')
      return false
    }
  }

  /**
   * Clear error message
   */
  function clearError() {
    error.value = null
  }

  // Computed properties
  const isLocked = computed(() => lockoutSeconds.value > 0)

  return {
    // State
    isAuthenticated,
    currentUser,
    failureCount,
    lockoutSeconds,
    isLoading,
    error,
    isLocked,

    // Methods
    login,
    logout,
    checkAuth,
    clearError
  }
}
