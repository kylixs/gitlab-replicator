import CryptoJS from 'crypto-js'

/**
 * SCRAM-SHA-256 Client Utility
 *
 * Implements client-side SCRAM-SHA-256 authentication algorithm
 *
 * @author GitLab Mirror Team
 */

/**
 * PBKDF2 key derivation
 *
 * @param password Password string
 * @param saltHex Salt value (hex encoded)
 * @param iterations Iteration count
 * @returns WordArray (256 bits)
 */
function pbkdf2(password: string, saltHex: string, iterations: number): CryptoJS.lib.WordArray {
  const salt = CryptoJS.enc.Hex.parse(saltHex)
  return CryptoJS.PBKDF2(password, salt, {
    keySize: 256 / 32,
    iterations: iterations,
    hasher: CryptoJS.algo.SHA256
  })
}

/**
 * XOR two WordArrays
 *
 * @param a First WordArray
 * @param b Second WordArray
 * @returns XOR result as hex string
 */
function xor(a: CryptoJS.lib.WordArray, b: CryptoJS.lib.WordArray): string {
  const aWords = a.words
  const bWords = b.words
  const resultWords: number[] = []
  const minLength = Math.min(aWords.length, bWords.length)

  for (let i = 0; i < minLength; i++) {
    resultWords[i] = (aWords[i] ?? 0) ^ (bWords[i] ?? 0)
  }

  return CryptoJS.lib.WordArray.create(resultWords, a.sigBytes).toString(CryptoJS.enc.Hex)
}

/**
 * Calculate ClientProof for SCRAM authentication
 *
 * @param username Username
 * @param password Password
 * @param challenge Challenge string from server
 * @param saltHex Salt value (hex encoded)
 * @param iterations PBKDF2 iteration count
 * @returns ClientProof as hex string
 */
export function calculateClientProof(
  username: string,
  password: string,
  challenge: string,
  saltHex: string,
  iterations: number
): string {
  // Step 1: SaltedPassword = PBKDF2(password, salt, iterations)
  const saltedPassword = pbkdf2(password, saltHex, iterations)

  // Step 2: ClientKey = HMAC-SHA256(SaltedPassword, "Client Key")
  const clientKey = CryptoJS.HmacSHA256('Client Key', saltedPassword)

  // Step 3: StoredKey = SHA256(ClientKey)
  const storedKey = CryptoJS.SHA256(clientKey)

  // Step 4: AuthMessage = username + ":" + challenge
  const authMessage = `${username}:${challenge}`

  // Step 5: ClientSignature = HMAC-SHA256(StoredKey, AuthMessage)
  const clientSignature = CryptoJS.HmacSHA256(authMessage, storedKey)

  // Step 6: ClientProof = XOR(ClientKey, ClientSignature)
  const clientProof = xor(clientKey, clientSignature)

  return clientProof
}
