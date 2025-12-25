package com.gitlab.mirror.server.service.auth;

import com.gitlab.mirror.server.entity.LoginAuditLog;
import com.gitlab.mirror.server.entity.LoginAuditLog.LoginResult;
import com.gitlab.mirror.server.mapper.LoginAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Login Audit Service
 * <p>
 * Async audit logging for security tracking
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private final LoginAuditLogMapper loginAuditLogMapper;

    /**
     * Record successful login (async)
     *
     * @param username  Username
     * @param ip        IP address
     * @param userAgent User agent
     */
    @Async("auditExecutor")
    public void recordLoginSuccess(String username, String ip, String userAgent) {
        try {
            LoginAuditLog auditLog = new LoginAuditLog();
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setUserAgent(userAgent);
            auditLog.setLoginResult(LoginResult.SUCCESS);
            loginAuditLogMapper.insert(auditLog);

            log.debug("Login success recorded - Username: {}, IP: {}", username, ip);
        } catch (Exception e) {
            log.error("Failed to record login success", e);
        }
    }

    /**
     * Record failed login (async)
     *
     * @param username      Username
     * @param ip            IP address
     * @param userAgent     User agent
     * @param failureReason Failure reason
     */
    @Async("auditExecutor")
    public void recordLoginFailure(String username, String ip, String userAgent, String failureReason) {
        try {
            LoginAuditLog auditLog = new LoginAuditLog();
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setUserAgent(userAgent);
            auditLog.setLoginResult(LoginResult.FAILURE);
            auditLog.setFailureReason(failureReason);
            loginAuditLogMapper.insert(auditLog);

            log.debug("Login failure recorded - Username: {}, IP: {}, Reason: {}", username, ip, failureReason);
        } catch (Exception e) {
            log.error("Failed to record login failure", e);
        }
    }

    /**
     * Record account locked event (async)
     *
     * @param username  Username
     * @param ip        IP address
     * @param userAgent User agent
     */
    @Async("auditExecutor")
    public void recordAccountLocked(String username, String ip, String userAgent) {
        try {
            LoginAuditLog auditLog = new LoginAuditLog();
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setUserAgent(userAgent);
            auditLog.setLoginResult(LoginResult.LOCKED);
            auditLog.setFailureReason("Account locked due to too many failed attempts");
            loginAuditLogMapper.insert(auditLog);

            log.warn("Account locked recorded - Username: {}, IP: {}", username, ip);
        } catch (Exception e) {
            log.error("Failed to record account locked", e);
        }
    }

    /**
     * Record rate limited event (async)
     *
     * @param username  Username
     * @param ip        IP address
     * @param userAgent User agent
     */
    @Async("auditExecutor")
    public void recordRateLimited(String username, String ip, String userAgent) {
        try {
            LoginAuditLog auditLog = new LoginAuditLog();
            auditLog.setUsername(username);
            auditLog.setIpAddress(ip);
            auditLog.setUserAgent(userAgent);
            auditLog.setLoginResult(LoginResult.RATE_LIMITED);
            auditLog.setFailureReason("Rate limit exceeded");
            loginAuditLogMapper.insert(auditLog);

            log.warn("Rate limited recorded - Username: {}, IP: {}", username, ip);
        } catch (Exception e) {
            log.error("Failed to record rate limited", e);
        }
    }

    /**
     * Get user login history
     *
     * @param username Username
     * @param limit    Maximum records
     * @return Login history
     */
    public List<LoginAuditLog> getUserLoginHistory(String username, int limit) {
        return loginAuditLogMapper.selectByUsername(username, limit);
    }

    /**
     * Get IP login history
     *
     * @param ip    IP address
     * @param limit Maximum records
     * @return Login history
     */
    public List<LoginAuditLog> getIpLoginHistory(String ip, int limit) {
        return loginAuditLogMapper.selectByIpAddress(ip, limit);
    }

    /**
     * Clean up old audit logs
     *
     * @param retentionDays Retention period in days
     * @return Number of deleted records
     */
    @Transactional
    public int cleanupOldLogs(int retentionDays) {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        int deleted = loginAuditLogMapper.deleteOldRecords(before);
        log.info("Cleaned up {} old audit log records (older than {} days)", deleted, retentionDays);
        return deleted;
    }
}
