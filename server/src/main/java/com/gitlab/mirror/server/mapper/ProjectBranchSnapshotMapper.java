package com.gitlab.mirror.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gitlab.mirror.server.entity.ProjectBranchSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Project Branch Snapshot Mapper
 *
 * @author GitLab Mirror Team
 */
@Mapper
public interface ProjectBranchSnapshotMapper extends BaseMapper<ProjectBranchSnapshot> {

    /**
     * Get all branches for a project
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @return List of branch snapshots
     */
    @Select("SELECT * FROM project_branch_snapshot " +
            "WHERE sync_project_id = #{syncProjectId} AND project_type = #{projectType} " +
            "ORDER BY is_default DESC, branch_name ASC")
    List<ProjectBranchSnapshot> selectByProject(
            @Param("syncProjectId") Long syncProjectId,
            @Param("projectType") String projectType
    );

    /**
     * Get specific branch snapshot
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @param branchName    Branch name
     * @return Branch snapshot
     */
    @Select("SELECT * FROM project_branch_snapshot " +
            "WHERE sync_project_id = #{syncProjectId} " +
            "AND project_type = #{projectType} " +
            "AND branch_name = #{branchName}")
    ProjectBranchSnapshot selectByBranch(
            @Param("syncProjectId") Long syncProjectId,
            @Param("projectType") String projectType,
            @Param("branchName") String branchName
    );

    /**
     * Delete all branches for a project
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @return Number of deleted rows
     */
    @Delete("DELETE FROM project_branch_snapshot " +
            "WHERE sync_project_id = #{syncProjectId} AND project_type = #{projectType}")
    int deleteByProject(
            @Param("syncProjectId") Long syncProjectId,
            @Param("projectType") String projectType
    );

    /**
     * Count branches for a project
     *
     * @param syncProjectId Sync project ID
     * @param projectType   Project type (source/target)
     * @return Branch count
     */
    @Select("SELECT COUNT(*) FROM project_branch_snapshot " +
            "WHERE sync_project_id = #{syncProjectId} AND project_type = #{projectType}")
    int countByProject(
            @Param("syncProjectId") Long syncProjectId,
            @Param("projectType") String projectType
    );
}
