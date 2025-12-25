/**
 * Authentication API Types
 */

export interface ChallengeRequest {
  username: string
}

export interface ChallengeResponse {
  challenge: string
  salt: string
  iterations: number
  expiresAt: string
}

export interface LoginRequest {
  username: string
  challenge: string
  clientProof: string
}

export interface UserInfo {
  username: string
  displayName: string
}

export interface LoginResponse {
  token: string
  expiresAt: string
  user: UserInfo
}

export interface TokenVerifyResponse {
  valid: boolean
  expiresAt?: string
  user?: UserInfo
}

export interface ApiError {
  code: string
  message: string
  retryAfter?: number
  failedAttempts?: number
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiError
}

export enum ErrorCode {
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  AUTHENTICATION_ERROR = 'AUTHENTICATION_ERROR',
  ACCOUNT_LOCKED = 'ACCOUNT_LOCKED',
  TOO_MANY_REQUESTS = 'TOO_MANY_REQUESTS',
  INTERNAL_ERROR = 'INTERNAL_ERROR'
}
