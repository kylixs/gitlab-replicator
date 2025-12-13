package com.gitlab.mirror.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application Context Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
class GitLabMirrorApplicationTest {

    @Test
    void contextLoads() {
        // Verify Spring context loads successfully with database
    }
}
