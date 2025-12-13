package com.gitlab.mirror.server.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gitlab.mirror.server.api.dto.ApiResponse;
import com.gitlab.mirror.server.api.dto.EventDTO;
import com.gitlab.mirror.server.api.dto.PageResponse;
import com.gitlab.mirror.server.entity.SyncEvent;
import com.gitlab.mirror.server.service.EventManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Event Query API Controller
 *
 * @author GitLab Mirror Team
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventManagementService eventManagementService;

    public EventController(EventManagementService eventManagementService) {
        this.eventManagementService = eventManagementService;
    }

    /**
     * Query events with multi-dimensional filters and pagination
     */
    @GetMapping
    public ApiResponse<PageResponse<EventDTO>> getEvents(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {

        IPage<SyncEvent> result = eventManagementService.queryEvents(
                page, size, projectId, eventType, status, startTime, endTime);

        // Load all unique project IDs
        List<Long> projectIds = result.getRecords().stream()
                .map(SyncEvent::getSyncProjectId)
                .distinct()
                .collect(Collectors.toList());

        // Batch load projects via service
        Map<Long, String> projectKeyMap = eventManagementService.getProjectKeys(projectIds);

        // Convert to DTOs
        List<EventDTO> eventDTOs = result.getRecords().stream()
                .map(event -> {
                    String projectKey = projectKeyMap.get(event.getSyncProjectId());
                    return EventDTO.from(event, projectKey);
                })
                .collect(Collectors.toList());

        PageResponse<EventDTO> pageResponse = new PageResponse<>();
        pageResponse.setItems(eventDTOs);
        pageResponse.setTotal(result.getTotal());
        pageResponse.setPage((int) result.getCurrent());
        pageResponse.setPageSize((int) result.getSize());
        pageResponse.setTotalPages((int) result.getPages());

        return ApiResponse.success(pageResponse);
    }
}
