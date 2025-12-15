package com.gitlab.mirror.server.service.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Project Change Details
 * <p>
 * Records what changed in a project during scan.
 *
 * @author GitLab Mirror Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectChange {

    /**
     * Project key (path with namespace)
     */
    private String projectKey;

    /**
     * Project type (source/target)
     */
    private String projectType;

    /**
     * List of field changes
     */
    @Builder.Default
    private List<FieldChange> fieldChanges = new ArrayList<>();

    /**
     * Field Change Details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldChange {
        /**
         * Field name
         */
        private String fieldName;

        /**
         * Old value
         */
        private String oldValue;

        /**
         * New value
         */
        private String newValue;
    }

    /**
     * Add a field change
     */
    public void addChange(String fieldName, Object oldValue, Object newValue) {
        FieldChange change = FieldChange.builder()
                .fieldName(fieldName)
                .oldValue(oldValue != null ? oldValue.toString() : "null")
                .newValue(newValue != null ? newValue.toString() : "null")
                .build();
        fieldChanges.add(change);
    }

    /**
     * Check if there are any changes
     */
    public boolean hasChanges() {
        return !fieldChanges.isEmpty();
    }
}
