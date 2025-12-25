import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { useAuth } from './useAuth'
import * as authApi from '@/api/auth'

// Mock auth API
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  verifyToken: vi.fn(),
  AuthApiError: class AuthApiError extends Error {
    code: string
    retryAfter?: number
    failedAttempts?: number

    constructor(error: { code: string; message: string; retryAfter?: number; failedAttempts?: number }) {
      super(error.message)
      this.name = 'AuthApiError'
      this.code = error.code
      this.retryAfter = error.retryAfter
      this.failedAttempts = error.failedAttempts
    }
  }
}))

describe('useAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('login', () => {
    it('should login successfully', async () => {
      const mockLoginResponse = {
        token: 'test-token',
        expiresAt: '2025-12-25T18:00:00Z',
        user: {
          username: 'testuser',
          displayName: 'Test User'
        }
      }

      vi.mocked(authApi.login).mockResolvedValue(mockLoginResponse)

      const { login, isAuthenticated, currentUser, failureCount } = useAuth()

      const result = await login('testuser', 'password')

      expect(result).toBe(true)
      expect(isAuthenticated.value).toBe(true)
      expect(currentUser.value).toEqual(mockLoginResponse.user)
      expect(failureCount.value).toBe(0)
    })

    it('should handle login failure', async () => {
      const mockError = new authApi.AuthApiError({
        code: 'AUTHENTICATION_ERROR',
        message: 'Invalid credentials'
      })

      vi.mocked(authApi.login).mockRejectedValue(mockError)

      const { login, isAuthenticated, currentUser, failureCount, error } = useAuth()

      const result = await login('testuser', 'wrongpassword')

      expect(result).toBe(false)
      expect(isAuthenticated.value).toBe(false)
      expect(currentUser.value).toBeNull()
      expect(failureCount.value).toBe(1)
      expect(error.value).toBe('Invalid credentials')
    })

    it('should handle account locked error', async () => {
      const mockError = new authApi.AuthApiError({
        code: 'ACCOUNT_LOCKED',
        message: 'Account locked',
        retryAfter: 300,
        failedAttempts: 5
      })

      vi.mocked(authApi.login).mockRejectedValue(mockError)

      const { login, lockoutSeconds, failureCount, isLocked } = useAuth()

      await login('testuser', 'password')

      expect(lockoutSeconds.value).toBe(300)
      expect(failureCount.value).toBe(5)
      expect(isLocked.value).toBe(true)
    })

    it('should countdown lockout timer', async () => {
      const mockError = new authApi.AuthApiError({
        code: 'ACCOUNT_LOCKED',
        message: 'Account locked',
        retryAfter: 5,
        failedAttempts: 5
      })

      vi.mocked(authApi.login).mockRejectedValue(mockError)

      const { login, lockoutSeconds, failureCount, isLocked } = useAuth()

      await login('testuser', 'password')

      expect(lockoutSeconds.value).toBe(5)
      expect(isLocked.value).toBe(true)

      // Advance timer by 1 second
      vi.advanceTimersByTime(1000)
      expect(lockoutSeconds.value).toBe(4)

      // Advance timer by 4 more seconds
      vi.advanceTimersByTime(4000)
      expect(lockoutSeconds.value).toBe(0)
      expect(isLocked.value).toBe(false)
      expect(failureCount.value).toBe(0)
    })

    it('should increment failure count on each failed attempt', async () => {
      // Reset the module to get a fresh instance
      vi.resetModules()
      const { useAuth: freshUseAuth } = await import('./useAuth')

      const mockError = new authApi.AuthApiError({
        code: 'AUTHENTICATION_ERROR',
        message: 'Invalid credentials'
      })

      vi.mocked(authApi.login).mockRejectedValue(mockError)

      const { login, failureCount } = freshUseAuth()

      await login('testuser', 'wrong1')
      expect(failureCount.value).toBe(1)

      await login('testuser', 'wrong2')
      expect(failureCount.value).toBe(2)

      await login('testuser', 'wrong3')
      expect(failureCount.value).toBe(3)
    })

    it('should reset failure count on successful login', async () => {
      // Reset the module to get a fresh instance
      vi.resetModules()
      const { useAuth: freshUseAuth } = await import('./useAuth')

      const mockError = new authApi.AuthApiError({
        code: 'AUTHENTICATION_ERROR',
        message: 'Invalid credentials'
      })

      const mockSuccess = {
        token: 'test-token',
        expiresAt: '2025-12-25T18:00:00Z',
        user: {
          username: 'testuser',
          displayName: 'Test User'
        }
      }

      vi.mocked(authApi.login)
        .mockRejectedValueOnce(mockError)
        .mockRejectedValueOnce(mockError)
        .mockResolvedValueOnce(mockSuccess)

      const { login, failureCount } = freshUseAuth()

      await login('testuser', 'wrong1')
      expect(failureCount.value).toBe(1)

      await login('testuser', 'wrong2')
      expect(failureCount.value).toBe(2)

      await login('testuser', 'correct')
      expect(failureCount.value).toBe(0)
    })
  })

  describe('logout', () => {
    it('should logout successfully', async () => {
      vi.mocked(authApi.logout).mockResolvedValue(undefined)

      const { logout, isAuthenticated, currentUser } = useAuth()

      // Set initial authenticated state
      isAuthenticated.value = true
      currentUser.value = { username: 'testuser', displayName: 'Test User' }

      await logout()

      expect(isAuthenticated.value).toBe(false)
      expect(currentUser.value).toBeNull()
    })

    it('should clear state even if logout API fails', async () => {
      vi.mocked(authApi.logout).mockRejectedValue(new Error('Network error'))

      const { logout, isAuthenticated, currentUser } = useAuth()

      // Set initial authenticated state
      isAuthenticated.value = true
      currentUser.value = { username: 'testuser', displayName: 'Test User' }

      await logout()

      expect(isAuthenticated.value).toBe(false)
      expect(currentUser.value).toBeNull()
    })

    it('should clear lockout timer on logout', async () => {
      vi.mocked(authApi.logout).mockResolvedValue(undefined)

      const { logout, lockoutSeconds, failureCount } = useAuth()

      // Set lockout state
      lockoutSeconds.value = 100
      failureCount.value = 5

      await logout()

      expect(lockoutSeconds.value).toBe(0)
      expect(failureCount.value).toBe(0)
    })
  })

  describe('checkAuth', () => {
    it('should return false if no token in localStorage', async () => {
      const { checkAuth, isAuthenticated } = useAuth()

      const result = await checkAuth()

      expect(result).toBe(false)
      expect(isAuthenticated.value).toBe(false)
    })

    it('should verify token and set authenticated state', async () => {
      localStorage.setItem('auth_token', 'test-token')

      const mockVerifyResponse = {
        valid: true,
        expiresAt: '2025-12-25T18:00:00Z',
        user: {
          username: 'testuser',
          displayName: 'Test User'
        }
      }

      vi.mocked(authApi.verifyToken).mockResolvedValue(mockVerifyResponse)

      const { checkAuth, isAuthenticated, currentUser } = useAuth()

      const result = await checkAuth()

      expect(result).toBe(true)
      expect(isAuthenticated.value).toBe(true)
      expect(currentUser.value).toEqual(mockVerifyResponse.user)
    })

    it('should clear token if verification fails', async () => {
      localStorage.setItem('auth_token', 'invalid-token')

      vi.mocked(authApi.verifyToken).mockRejectedValue(
        new authApi.AuthApiError({ code: 'AUTHENTICATION_ERROR', message: 'Invalid token' })
      )

      const { checkAuth, isAuthenticated } = useAuth()

      const result = await checkAuth()

      expect(result).toBe(false)
      expect(isAuthenticated.value).toBe(false)
      expect(localStorage.getItem('auth_token')).toBeNull()
    })
  })

  describe('clearError', () => {
    it('should clear error message', async () => {
      const mockError = new authApi.AuthApiError({
        code: 'AUTHENTICATION_ERROR',
        message: 'Invalid credentials'
      })

      vi.mocked(authApi.login).mockRejectedValue(mockError)

      const { login, error, clearError } = useAuth()

      await login('testuser', 'wrongpassword')
      expect(error.value).toBe('Invalid credentials')

      clearError()
      expect(error.value).toBeNull()
    })
  })
})
