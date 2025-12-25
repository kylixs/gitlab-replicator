package com.gitlab.mirror.server.config;

import com.gitlab.mirror.server.entity.User;
import com.gitlab.mirror.server.mapper.UserMapper;
import com.gitlab.mirror.server.service.auth.ScramUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Database Initializer
 * <p>
 * Initializes default admin user on application startup
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements CommandLineRunner {

    private final UserMapper userMapper;

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "Admin@123";
    private static final String DEFAULT_DISPLAY_NAME = "Administrator";

    @Override
    public void run(String... args) {
        initializeDefaultAdmin();
    }

    /**
     * Initialize default admin user if not exists
     */
    private void initializeDefaultAdmin() {
        // Check if admin user already exists
        User existingAdmin = userMapper.selectByUsername(DEFAULT_USERNAME);
        if (existingAdmin != null) {
            log.info("Default admin user already exists, skipping initialization");
            return;
        }

        try {
            // Generate random salt
            String salt = ScramUtils.generateSaltHex();

            // Calculate StoredKey using SCRAM-SHA-256
            int iterations = ScramUtils.getDefaultIterations();
            String storedKey = ScramUtils.calculateStoredKey(DEFAULT_PASSWORD, salt, iterations);

            // Create admin user entity
            User admin = new User();
            admin.setUsername(DEFAULT_USERNAME);
            admin.setStoredKey(storedKey);
            admin.setSalt(salt);
            admin.setIterations(iterations);
            admin.setDisplayName(DEFAULT_DISPLAY_NAME);
            admin.setEnabled(true);

            // Insert into database
            userMapper.insert(admin);

            log.warn("========================================");
            log.warn("Default admin user created successfully");
            log.warn("Username: {}", DEFAULT_USERNAME);
            log.warn("Password: {} (PLEASE CHANGE THIS IMMEDIATELY!)", DEFAULT_PASSWORD);
            log.warn("========================================");

        } catch (Exception e) {
            log.error("Failed to create default admin user", e);
            throw new RuntimeException("Failed to initialize default admin user", e);
        }
    }
}
