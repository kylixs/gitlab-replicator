package com.gitlab.mirror.server.vo;

import com.gitlab.mirror.server.entity.PushMirrorConfig;
import com.gitlab.mirror.server.entity.SourceProjectInfo;
import com.gitlab.mirror.server.entity.SyncProject;
import com.gitlab.mirror.server.entity.TargetProjectInfo;
import lombok.Data;

/**
 * Sync Project Detail VO
 * <p>
 * Complete project information with all related data
 *
 * @author GitLab Mirror Team
 */
@Data
public class SyncProjectDetailVO {
    /**
     * Sync project core info
     */
    private SyncProject syncProject;

    /**
     * Source project info
     */
    private SourceProjectInfo sourceProjectInfo;

    /**
     * Target project info
     */
    private TargetProjectInfo targetProjectInfo;

    /**
     * Push mirror configuration
     */
    private PushMirrorConfig pushMirrorConfig;
}
