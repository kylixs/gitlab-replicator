-- Migration: Create Authentication Tables
-- Version: 003
-- Description: Create users, auth_tokens, and login_audit_log tables for SCRAM-SHA-256 authentication
-- Date: 2025-12-25

-- ============================================================
-- Table: users
-- Description: User account table with SCRAM-SHA-256 credentials
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT 'Username (unique)',
    stored_key VARCHAR(64) NOT NULL COMMENT 'SCRAM StoredKey (hex encoded)',
    salt VARCHAR(32) NOT NULL COMMENT 'Salt for PBKDF2 (16 bytes hex encoded)',
    iterations INT NOT NULL DEFAULT 4096 COMMENT 'PBKDF2 iteration count',
    display_name VARCHAR(100) NOT NULL COMMENT 'Display name',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether the account is enabled',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    INDEX idx_username (username),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User account table';

-- ============================================================
-- Table: auth_tokens
-- Description: Authentication token table for session management
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    token VARCHAR(64) NOT NULL UNIQUE COMMENT 'Token value (UUID)',
    user_id BIGINT NOT NULL COMMENT 'User ID (foreign key)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Token creation timestamp',
    expires_at TIMESTAMP NOT NULL COMMENT 'Token expiration timestamp',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Last used timestamp',
    CONSTRAINT fk_auth_tokens_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Authentication token table';

-- ============================================================
-- Table: login_audit_log
-- Description: Login audit log for security tracking
-- ============================================================
CREATE TABLE IF NOT EXISTS login_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    username VARCHAR(50) NOT NULL COMMENT 'Username attempting login',
    ip_address VARCHAR(45) NOT NULL COMMENT 'Client IP address (supports IPv6)',
    user_agent TEXT COMMENT 'User agent string',
    login_result ENUM('SUCCESS', 'FAILURE', 'LOCKED', 'RATE_LIMITED') NOT NULL COMMENT 'Login attempt result',
    failure_reason VARCHAR(100) COMMENT 'Reason for failure',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Log creation timestamp',
    INDEX idx_username (username),
    INDEX idx_ip_address (ip_address),
    INDEX idx_created_at (created_at),
    INDEX idx_login_result (login_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Login audit log table';
