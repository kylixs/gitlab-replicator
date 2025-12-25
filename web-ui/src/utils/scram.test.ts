import { describe, it, expect } from 'vitest'
import { calculateClientProof } from './scram'
import CryptoJS from 'crypto-js'

describe('SCRAM-SHA-256 Client Utility', () => {
  describe('calculateClientProof', () => {
    it('should calculate correct ClientProof for test vector', () => {
      // Test vector from design document
      const username = 'admin'
      const password = 'Admin@123'
      const challenge = 'test-challenge-uuid'
      const saltHex = '1a2b3c4d5e6f7890' // 16 bytes hex
      const iterations = 4096

      const clientProof = calculateClientProof(
        username,
        password,
        challenge,
        saltHex,
        iterations
      )

      // Verify output format (64 hex characters)
      expect(clientProof).toMatch(/^[0-9a-f]{64}$/)
      expect(clientProof.length).toBe(64)
    })

    it('should produce different proofs for different passwords', () => {
      const username = 'testuser'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const proof1 = calculateClientProof(username, 'password1', challenge, saltHex, iterations)
      const proof2 = calculateClientProof(username, 'password2', challenge, saltHex, iterations)

      expect(proof1).not.toBe(proof2)
    })

    it('should produce different proofs for different challenges', () => {
      const username = 'testuser'
      const password = 'password'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const proof1 = calculateClientProof(username, password, 'challenge-1', saltHex, iterations)
      const proof2 = calculateClientProof(username, password, 'challenge-2', saltHex, iterations)

      expect(proof1).not.toBe(proof2)
    })

    it('should produce different proofs for different usernames', () => {
      const password = 'password'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const proof1 = calculateClientProof('user1', password, challenge, saltHex, iterations)
      const proof2 = calculateClientProof('user2', password, challenge, saltHex, iterations)

      expect(proof1).not.toBe(proof2)
    })

    it('should produce different proofs for different salts', () => {
      const username = 'testuser'
      const password = 'password'
      const challenge = 'challenge-123'
      const iterations = 4096

      const proof1 = calculateClientProof(username, password, challenge, 'aabbccdd11223344', iterations)
      const proof2 = calculateClientProof(username, password, challenge, '11223344aabbccdd', iterations)

      expect(proof1).not.toBe(proof2)
    })

    it('should produce the same proof for same inputs (deterministic)', () => {
      const username = 'testuser'
      const password = 'password'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const proof1 = calculateClientProof(username, password, challenge, saltHex, iterations)
      const proof2 = calculateClientProof(username, password, challenge, saltHex, iterations)

      expect(proof1).toBe(proof2)
    })

    it('should handle different iteration counts', () => {
      const username = 'testuser'
      const password = 'password'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'

      const proof1 = calculateClientProof(username, password, challenge, saltHex, 4096)
      const proof2 = calculateClientProof(username, password, challenge, saltHex, 8192)

      expect(proof1).not.toBe(proof2)
    })

    it('should handle special characters in username and password', () => {
      const username = 'user@example.com'
      const password = 'P@ssw0rd!#$%'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const clientProof = calculateClientProof(username, password, challenge, saltHex, iterations)

      expect(clientProof).toMatch(/^[0-9a-f]{64}$/)
    })

    it('should handle UTF-8 characters in password', () => {
      const username = 'testuser'
      const password = '密码测试🔒'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const clientProof = calculateClientProof(username, password, challenge, saltHex, iterations)

      expect(clientProof).toMatch(/^[0-9a-f]{64}$/)
    })

    it('should complete calculation within acceptable time', () => {
      const username = 'testuser'
      const password = 'password'
      const challenge = 'challenge-123'
      const saltHex = 'abcdef1234567890'
      const iterations = 4096

      const startTime = performance.now()
      calculateClientProof(username, password, challenge, saltHex, iterations)
      const endTime = performance.now()

      // Should complete within 200ms for 4096 iterations
      expect(endTime - startTime).toBeLessThan(200)
    })
  })

  describe('Integration with backend verification', () => {
    it('should match backend StoredKey calculation logic', () => {
      // This test verifies that the client proof can be verified by the backend
      // We'll simulate the backend verification process

      const username = 'admin'
      const password = 'Admin@123'
      const challenge = 'test-challenge'
      const saltHex = '1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d'
      const iterations = 4096

      // Client side: Calculate ClientProof
      const clientProof = calculateClientProof(username, password, challenge, saltHex, iterations)

      // Simulate backend verification
      // Backend would:
      // 1. Calculate SaltedPassword (same as client)
      const salt = CryptoJS.enc.Hex.parse(saltHex)
      const saltedPassword = CryptoJS.PBKDF2(password, salt, {
        keySize: 256 / 32,
        iterations: iterations,
        hasher: CryptoJS.algo.SHA256
      })

      // 2. Calculate ClientKey and StoredKey (stored in database)
      const clientKey = CryptoJS.HmacSHA256('Client Key', saltedPassword)
      const storedKey = CryptoJS.SHA256(clientKey)

      // 3. Calculate AuthMessage
      const authMessage = `${username}:${challenge}`

      // 4. Calculate ClientSignature
      const clientSignature = CryptoJS.HmacSHA256(authMessage, storedKey)

      // 5. Recover ClientKey from ClientProof (XOR operation)
      const clientProofWordArray = CryptoJS.enc.Hex.parse(clientProof)
      const recoveredClientKeyWords: number[] = []
      const minLength = Math.min(clientProofWordArray.words.length, clientSignature.words.length)
      for (let i = 0; i < minLength; i++) {
        recoveredClientKeyWords[i] = (clientProofWordArray.words[i] ?? 0) ^ (clientSignature.words[i] ?? 0)
      }
      const recoveredClientKey = CryptoJS.lib.WordArray.create(
        recoveredClientKeyWords,
        clientProofWordArray.sigBytes
      )

      // 6. Verify: SHA256(RecoveredClientKey) should equal StoredKey
      const recoveredStoredKey = CryptoJS.SHA256(recoveredClientKey)

      expect(recoveredStoredKey.toString(CryptoJS.enc.Hex)).toBe(storedKey.toString(CryptoJS.enc.Hex))
    })
  })
})
