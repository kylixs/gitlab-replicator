import { describe, it, expect, beforeEach, vi } from 'vitest'
import { login, logout, verifyToken, getChallenge, getStoredToken, hasAuthToken, AuthApiError } from './auth'
import * as scramUtils from '@/utils/scram'

// Mock axios client
vi.mock('./client', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn()
  }
}))

import client from './client'

describe('Authentication API Client', () => {
  beforeEach(() => {
    // Clear localStorage
    localStorage.clear()
    // Clear all mocks
    vi.clearAllMocks()
  })

  describe('getChallenge', () => {
    it('should get challenge successfully', async () => {
      const mockResponse = {
        data: {
          success: true,
          data: {
            challenge: 'test-challenge-uuid',
            salt: 'abc123',
            iterations: 4096,
            expiresAt: '2025-12-25T10:00:00Z'
          }
        }
      }

      vi.mocked(client.post).mockResolvedValue(mockResponse)

      const result = await getChallenge('testuser')

      expect(client.post).toHaveBeenCalledWith('/auth/challenge', { username: 'testuser' })
      expect(result.challenge).toBe('test-challenge-uuid')
      expect(result.salt).toBe('abc123')
      expect(result.iterations).toBe(4096)
    })

    it('should throw AuthApiError when challenge request fails', async () => {
      const mockResponse = {
        data: {
          success: false,
          error: {
            code: 'USER_NOT_FOUND',
            message: 'User not found'
          }
        }
      }

      vi.mocked(client.post).mockResolvedValue(mockResponse)

      await expect(getChallenge('nonexistent')).rejects.toThrow(AuthApiError)
    })
  })

  describe('login', () => {
    it('should login successfully and store token', async () => {
      // Mock getChallenge response
      const mockChallengeResponse = {
        data: {
          success: true,
          data: {
            challenge: 'test-challenge',
            salt: 'abc123',
            iterations: 4096,
            expiresAt: '2025-12-25T10:00:00Z'
          }
        }
      }

      // Mock login response
      const mockLoginResponse = {
        data: {
          success: true,
          data: {
            token: 'test-token-uuid',
            expiresAt: '2025-12-25T18:00:00Z',
            user: {
              username: 'testuser',
              displayName: 'Test User'
            }
          }
        }
      }

      vi.mocked(client.post)
        .mockResolvedValueOnce(mockChallengeResponse)
        .mockResolvedValueOnce(mockLoginResponse)

      // Mock calculateClientProof
      vi.spyOn(scramUtils, 'calculateClientProof').mockReturnValue('mock-client-proof')

      const result = await login('testuser', 'password')

      expect(result.token).toBe('test-token-uuid')
      expect(result.user.username).toBe('testuser')
      expect(localStorage.getItem('auth_token')).toBe('test-token-uuid')
    })

    it('should throw AuthApiError on invalid credentials', async () => {
      const mockChallengeResponse = {
        data: {
          success: true,
          data: {
            challenge: 'test-challenge',
            salt: 'abc123',
            iterations: 4096,
            expiresAt: '2025-12-25T10:00:00Z'
          }
        }
      }

      const mockLoginResponse = {
        data: {
          success: false,
          error: {
            code: 'AUTHENTICATION_ERROR',
            message: 'Invalid username or password'
          }
        }
      }

      vi.mocked(client.post)
        .mockResolvedValueOnce(mockChallengeResponse)
        .mockResolvedValueOnce(mockLoginResponse)

      vi.spyOn(scramUtils, 'calculateClientProof').mockReturnValue('mock-client-proof')

      await expect(login('testuser', 'wrongpassword')).rejects.toThrow(AuthApiError)
      expect(localStorage.getItem('auth_token')).toBeNull()
    })

    it('should throw AuthApiError with account locked info', async () => {
      const mockChallengeResponse = {
        data: {
          success: true,
          data: {
            challenge: 'test-challenge',
            salt: 'abc123',
            iterations: 4096,
            expiresAt: '2025-12-25T10:00:00Z'
          }
        }
      }

      const mockLoginResponse = {
        data: {
          success: false,
          error: {
            code: 'ACCOUNT_LOCKED',
            message: 'Account locked due to too many failed attempts',
            retryAfter: 300,
            failedAttempts: 5
          }
        }
      }

      vi.mocked(client.post)
        .mockResolvedValueOnce(mockChallengeResponse)
        .mockResolvedValueOnce(mockLoginResponse)

      vi.spyOn(scramUtils, 'calculateClientProof').mockReturnValue('mock-client-proof')

      try {
        await login('testuser', 'password')
        expect.fail('Should have thrown AuthApiError')
      } catch (error) {
        expect(error).toBeInstanceOf(AuthApiError)
        if (error instanceof AuthApiError) {
          expect(error.code).toBe('ACCOUNT_LOCKED')
          expect(error.retryAfter).toBe(300)
          expect(error.failedAttempts).toBe(5)
        }
      }
    })
  })

  describe('logout', () => {
    it('should clear token from localStorage', async () => {
      localStorage.setItem('auth_token', 'test-token')

      vi.mocked(client.post).mockResolvedValue({ data: { success: true } })

      await logout()

      expect(client.post).toHaveBeenCalledWith('/auth/logout')
      expect(localStorage.getItem('auth_token')).toBeNull()
    })

    it('should clear token even if logout API fails', async () => {
      localStorage.setItem('auth_token', 'test-token')

      vi.mocked(client.post).mockRejectedValue(new Error('Network error'))

      await logout()

      expect(localStorage.getItem('auth_token')).toBeNull()
    })

    it('should not call API if no token exists', async () => {
      await logout()

      expect(client.post).not.toHaveBeenCalled()
    })
  })

  describe('verifyToken', () => {
    it('should verify token successfully', async () => {
      const mockResponse = {
        data: {
          success: true,
          data: {
            valid: true,
            expiresAt: '2025-12-25T18:00:00Z',
            user: {
              username: 'testuser',
              displayName: 'Test User'
            }
          }
        }
      }

      vi.mocked(client.get).mockResolvedValue(mockResponse)

      const result = await verifyToken()

      expect(client.get).toHaveBeenCalledWith('/auth/verify')
      expect(result.valid).toBe(true)
      expect(result.user?.username).toBe('testuser')
    })

    it('should throw AuthApiError on invalid token', async () => {
      const mockResponse = {
        data: {
          success: false,
          error: {
            code: 'AUTHENTICATION_ERROR',
            message: 'Invalid or expired token'
          }
        }
      }

      vi.mocked(client.get).mockResolvedValue(mockResponse)

      await expect(verifyToken()).rejects.toThrow(AuthApiError)
    })
  })

  describe('getStoredToken', () => {
    it('should return stored token', () => {
      localStorage.setItem('auth_token', 'test-token')
      expect(getStoredToken()).toBe('test-token')
    })

    it('should return null if no token', () => {
      expect(getStoredToken()).toBeNull()
    })
  })

  describe('hasAuthToken', () => {
    it('should return true if token exists', () => {
      localStorage.setItem('auth_token', 'test-token')
      expect(hasAuthToken()).toBe(true)
    })

    it('should return false if no token', () => {
      expect(hasAuthToken()).toBe(false)
    })
  })
})
