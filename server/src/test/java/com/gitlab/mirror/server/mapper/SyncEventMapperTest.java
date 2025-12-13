package com.gitlab.mirror.server.mapper;

import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.entity.SyncProject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sync Event Mapper Test
 *
 * @author GitLab Mirror Team
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SyncEventMapperTest {

    @Autowired
    private SyncEventMapper syncEventMapper;

    @Autowired
    private SyncProjectMapper syncProjectMapper;

    @Test
    void testInsert() {
        // Create sync project first
        SyncProject syncProject = createSyncProject("group1/test-event-1");

        // Create sync event
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(syncProject.getId());
        event.setEventType(SyncEvent.EventType.SYNC_STARTED);
        event.setEventSource(SyncEvent.EventSource.POLLING);
        event.setStatus(SyncEvent.Status.RUNNING);
        event.setCommitSha("abc123def456");
        event.setRef("refs/heads/main");
        event.setBranchName("main");
        event.setEventTime(LocalDateTime.now());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("trigger", "manual");
        eventData.put("user", "admin");
        event.setEventData(eventData);

        // Insert
        int result = syncEventMapper.insert(event);

        // Verify
        assertThat(result).isEqualTo(1);
        assertThat(event.getId()).isNotNull();
    }

    @Test
    void testSelectById() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-event-2");
        SyncEvent event = createSyncEvent(syncProject.getId());

        // Query by ID
        SyncEvent found = syncEventMapper.selectById(event.getId());

        // Verify
        assertThat(found).isNotNull();
        assertThat(found.getEventType()).isEqualTo(SyncEvent.EventType.SYNC_STARTED);
        assertThat(found.getEventData()).isNotNull();
        assertThat(found.getEventData()).containsKey("trigger");
    }

    @Test
    void testUpdate() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-event-3");
        SyncEvent event = createSyncEvent(syncProject.getId());

        // Update
        event.setStatus(SyncEvent.Status.SUCCESS);
        event.setDurationSeconds(10);
        int result = syncEventMapper.updateById(event);

        // Verify
        assertThat(result).isEqualTo(1);
        SyncEvent updated = syncEventMapper.selectById(event.getId());
        assertThat(updated.getStatus()).isEqualTo(SyncEvent.Status.SUCCESS);
        assertThat(updated.getDurationSeconds()).isEqualTo(10);
    }

    @Test
    void testDelete() {
        // Create and insert
        SyncProject syncProject = createSyncProject("group1/test-event-4");
        SyncEvent event = createSyncEvent(syncProject.getId());

        // Delete
        int result = syncEventMapper.deleteById(event.getId());

        // Verify
        assertThat(result).isEqualTo(1);
        SyncEvent deleted = syncEventMapper.selectById(event.getId());
        assertThat(deleted).isNull();
    }

    private SyncProject createSyncProject(String projectKey) {
        SyncProject project = new SyncProject();
        project.setProjectKey(projectKey);
        project.setSyncMethod(SyncProject.SyncMethod.PUSH_MIRROR);
        project.setSyncStatus(SyncProject.SyncStatus.PENDING);
        project.setEnabled(true);
        syncProjectMapper.insert(project);
        return project;
    }

    private SyncEvent createSyncEvent(Long syncProjectId) {
        SyncEvent event = new SyncEvent();
        event.setSyncProjectId(syncProjectId);
        event.setEventType(SyncEvent.EventType.SYNC_STARTED);
        event.setEventSource(SyncEvent.EventSource.POLLING);
        event.setStatus(SyncEvent.Status.RUNNING);
        event.setCommitSha("abc123def456");
        event.setRef("refs/heads/main");
        event.setBranchName("main");
        event.setEventTime(LocalDateTime.now());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("trigger", "manual");
        event.setEventData(eventData);

        syncEventMapper.insert(event);
        return event;
    }
}
