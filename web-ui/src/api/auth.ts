import client from './client'
import { calculateClientProof } from '@/utils/scram'
import type {
  ChallengeRequest,
  ChallengeResponse,
  LoginRequest,
  LoginResponse,
  TokenVerifyResponse,
  ApiResponse,
  ApiError
} from './types/auth'

const TOKEN_KEY = 'auth_token'

/**
 * Authentication API Error
 */
export class AuthApiError extends Error {
  code: string
  retryAfter?: number
  failedAttempts?: number

  constructor(error: ApiError) {
    super(error.message)
    this.name = 'AuthApiError'
    this.code = error.code
    this.retryAfter = error.retryAfter
    this.failedAttempts = error.failedAttempts
  }
}

/**
 * Get challenge from server
 */
export async function getChallenge(username: string): Promise<ChallengeResponse> {
  try {
    const response = await client.post<ApiResponse<ChallengeResponse>>(
      '/auth/challenge',
      { username } as ChallengeRequest
    )

    if (!response.data.success || !response.data.data) {
      throw new AuthApiError(
        response.data.error || { code: 'UNKNOWN_ERROR', message: 'Failed to get challenge' }
      )
    }

    return response.data.data
  } catch (error: any) {
    if (error instanceof AuthApiError) {
      throw error
    }

    // Handle axios error
    if (error.response?.data?.error) {
      throw new AuthApiError(error.response.data.error)
    }

    throw new AuthApiError({
      code: 'NETWORK_ERROR',
      message: error.message || 'Network error occurred'
    })
  }
}

/**
 * Login with username and password
 */
export async function login(username: string, password: string): Promise<LoginResponse> {
  try {
    // Step 1: Get challenge
    const challengeResponse = await getChallenge(username)

    // Step 2: Calculate client proof
    const clientProof = calculateClientProof(
      username,
      password,
      challengeResponse.challenge,
      challengeResponse.salt,
      challengeResponse.iterations
    )

    // Step 3: Submit login request
    const loginRequest: LoginRequest = {
      username,
      challenge: challengeResponse.challenge,
      clientProof
    }

    const response = await client.post<ApiResponse<LoginResponse>>('/auth/login', loginRequest)

    if (!response.data.success || !response.data.data) {
      throw new AuthApiError(
        response.data.error || { code: 'UNKNOWN_ERROR', message: 'Login failed' }
      )
    }

    // Store token in localStorage
    const loginData = response.data.data
    localStorage.setItem(TOKEN_KEY, loginData.token)

    return loginData
  } catch (error: any) {
    if (error instanceof AuthApiError) {
      throw error
    }

    // Handle axios error
    if (error.response?.data?.error) {
      throw new AuthApiError(error.response.data.error)
    }

    throw new AuthApiError({
      code: 'NETWORK_ERROR',
      message: error.message || 'Network error occurred'
    })
  }
}

/**
 * Logout (clear token)
 */
export async function logout(): Promise<void> {
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) {
      return
    }

    // Call logout API (best effort, ignore errors)
    await client.post('/auth/logout').catch(() => {
      // Ignore logout API errors
    })
  } finally {
    // Always clear local token
    localStorage.removeItem(TOKEN_KEY)
  }
}

/**
 * Verify token validity
 */
export async function verifyToken(): Promise<TokenVerifyResponse> {
  try {
    const response = await client.get<ApiResponse<TokenVerifyResponse>>('/auth/verify')

    if (!response.data.success || !response.data.data) {
      throw new AuthApiError(
        response.data.error || { code: 'UNKNOWN_ERROR', message: 'Token verification failed' }
      )
    }

    return response.data.data
  } catch (error: any) {
    if (error instanceof AuthApiError) {
      throw error
    }

    // Handle axios error
    if (error.response?.data?.error) {
      throw new AuthApiError(error.response.data.error)
    }

    throw new AuthApiError({
      code: 'NETWORK_ERROR',
      message: error.message || 'Network error occurred'
    })
  }
}

/**
 * Get stored token from localStorage
 */
export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * Check if user is authenticated (has token)
 */
export function hasAuthToken(): boolean {
  return !!getStoredToken()
}
