package com.gitlab.mirror.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * GitLab Remote Mirror Model
 *
 * @author GitLab Mirror Team
 */
@Data
public class RemoteMirror {

    private Long id;

    private Boolean enabled;

    private String url;

    @JsonProperty("update_status")
    private String updateStatus;

    @JsonProperty("last_update_at")
    private LocalDateTime lastUpdateAt;

    @JsonProperty("last_update_started_at")
    private LocalDateTime lastUpdateStartedAt;

    @JsonProperty("last_successful_update_at")
    private LocalDateTime lastSuccessfulUpdateAt;

    @JsonProperty("last_error")
    private String lastError;

    @JsonProperty("only_protected_branches")
    private Boolean onlyProtectedBranches;

    @JsonProperty("keep_divergent_refs")
    private Boolean keepDivergentRefs;
}
