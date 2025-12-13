package com.gitlab.mirror.server.api.dto;

import com.gitlab.mirror.server.entity.SyncEvent;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Event DTO with project path
 *
 * @author GitLab Mirror Team
 */
@Data
public class EventDTO {
    private Long id;
    private Long syncProjectId;
    private String projectKey;
    private String eventType;
    private String eventSource;
    private String status;
    private String commitSha;
    private String ref;
    private String branchName;
    private Integer durationSeconds;
    private String errorMessage;
    private LocalDateTime eventTime;

    public static EventDTO from(SyncEvent event, String projectKey) {
        EventDTO dto = new EventDTO();
        dto.setId(event.getId());
        dto.setSyncProjectId(event.getSyncProjectId());
        dto.setProjectKey(projectKey);
        dto.setEventType(event.getEventType());
        dto.setEventSource(event.getEventSource());
        dto.setStatus(event.getStatus());
        dto.setCommitSha(event.getCommitSha());
        dto.setRef(event.getRef());
        dto.setBranchName(event.getBranchName());
        dto.setDurationSeconds(event.getDurationSeconds());
        dto.setErrorMessage(event.getErrorMessage());
        dto.setEventTime(event.getEventTime());
        return dto;
    }
}
