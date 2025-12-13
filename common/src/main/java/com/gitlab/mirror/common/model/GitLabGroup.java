package com.gitlab.mirror.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitLab Group Model
 *
 * @author GitLab Mirror Team
 */
@Data
public class GitLabGroup {

    private Long id;

    private String name;

    private String path;

    @JsonProperty("full_path")
    private String fullPath;

    @JsonProperty("parent_id")
    private Long parentId;

    private String description;

    private String visibility;

    @JsonProperty("web_url")
    private String webUrl;
}
