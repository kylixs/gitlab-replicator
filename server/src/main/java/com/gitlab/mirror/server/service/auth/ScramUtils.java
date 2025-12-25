package com.gitlab.mirror.server.service.auth;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

/**
 * SCRAM-SHA-256 Utility Class
 * <p>
 * Implements SCRAM-SHA-256 authentication mechanism (RFC 5802, RFC 7677)
 *
 * @author GitLab Mirror Team
 */
public class ScramUtils {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int DEFAULT_ITERATIONS = 4096;
    private static final int SALT_LENGTH = 16; // 16 bytes
    private static final int KEY_LENGTH = 256; // 256 bits

    /**
     * Calculate StoredKey from password
     * <p>
     * StoredKey = SHA256(ClientKey)
     * ClientKey = HMAC(SaltedPassword, "Client Key")
     * SaltedPassword = PBKDF2(password, salt, iterations)
     *
     * @param password   Plain text password
     * @param saltHex    Salt value (hex encoded)
     * @param iterations PBKDF2 iteration count
     * @return StoredKey (hex encoded)
     * @throws RuntimeException if cryptographic operation fails
     */
    public static String calculateStoredKey(String password, String saltHex, int iterations) {
        try {
            // Step 1: PBKDF2 to derive SaltedPassword
            byte[] salt = Hex.decodeHex(saltHex);
            byte[] saltedPassword = pbkdf2(password, salt, iterations);

            // Step 2: HMAC-SHA256 to calculate ClientKey
            byte[] clientKey = hmacSha256(saltedPassword, "Client Key");

            // Step 3: SHA-256 to calculate StoredKey
            byte[] storedKey = sha256(clientKey);

            // Return as hex string
            return Hex.encodeHexString(storedKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate StoredKey", e);
        }
    }

    /**
     * Verify ClientProof
     * <p>
     * Verification steps:
     * 1. Calculate AuthMessage = username + ":" + challenge
     * 2. Calculate ClientSignature = HMAC(StoredKey, AuthMessage)
     * 3. Recover ClientKey = XOR(ClientProof, ClientSignature)
     * 4. Verify SHA256(ClientKey) == StoredKey
     *
     * @param username    Username
     * @param challenge   Challenge code
     * @param clientProof ClientProof from client (hex encoded)
     * @param storedKey   StoredKey from database (hex encoded)
     * @return true if verification succeeds, false otherwise
     */
    public static boolean verifyClientProof(String username, String challenge, String clientProof, String storedKey) {
        try {
            // Step 1: Calculate AuthMessage
            String authMessage = username + ":" + challenge;

            // Step 2: Calculate ClientSignature = HMAC(StoredKey, AuthMessage)
            byte[] storedKeyBytes = Hex.decodeHex(storedKey);
            byte[] clientSignature = hmacSha256(storedKeyBytes, authMessage);

            // Step 3: Recover ClientKey = XOR(ClientProof, ClientSignature)
            byte[] clientProofBytes = Hex.decodeHex(clientProof);
            byte[] recoveredClientKey = xor(clientProofBytes, clientSignature);

            // Step 4: Verify SHA256(ClientKey) == StoredKey
            byte[] calculatedStoredKey = sha256(recoveredClientKey);
            return MessageDigest.isEqual(calculatedStoredKey, storedKeyBytes);

        } catch (Exception e) {
            return false; // Verification failed
        }
    }

    /**
     * PBKDF2 key derivation
     *
     * @param password   Password
     * @param salt       Salt bytes
     * @param iterations Iteration count
     * @return Derived key (32 bytes)
     * @throws NoSuchAlgorithmException if algorithm not found
     * @throws InvalidKeySpecException  if key spec is invalid
     */
    public static byte[] pbkdf2(String password, byte[] salt, int iterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * HMAC-SHA256 calculation
     *
     * @param key     Key bytes
     * @param message Message string
     * @return HMAC result (32 bytes)
     * @throws Exception if HMAC calculation fails
     */
    public static byte[] hmacSha256(byte[] key, String message) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 hash calculation
     *
     * @param data Input data
     * @return Hash result (32 bytes)
     * @throws NoSuchAlgorithmException if algorithm not found
     */
    public static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        return digest.digest(data);
    }

    /**
     * XOR operation on byte arrays
     *
     * @param a First byte array
     * @param b Second byte array
     * @return XOR result
     * @throws IllegalArgumentException if arrays have different lengths
     */
    public static byte[] xor(byte[] a, byte[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Byte arrays must have the same length");
        }
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte) (a[i] ^ b[i]);
        }
        return result;
    }

    /**
     * Generate random salt
     *
     * @return Salt bytes (16 bytes)
     */
    public static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Generate random salt (hex encoded)
     *
     * @return Salt hex string (32 characters)
     */
    public static String generateSaltHex() {
        return Hex.encodeHexString(generateSalt());
    }

    /**
     * Get default iteration count
     *
     * @return Default PBKDF2 iteration count
     */
    public static int getDefaultIterations() {
        return DEFAULT_ITERATIONS;
    }
}
