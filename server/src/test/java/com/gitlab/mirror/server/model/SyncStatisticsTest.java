package com.gitlab.mirror.server.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SyncStatistics
 *
 * @author GitLab Mirror Team
 */
class SyncStatisticsTest {

    @Test
    void testParseFromGitOutput_AllFields() {
        // Given
        String output = "BRANCHES_CREATED=5\n" +
                       "BRANCHES_UPDATED=3\n" +
                       "BRANCHES_DELETED=2\n" +
                       "COMMITS_PUSHED=23\n";

        // When
        SyncStatistics stats = SyncStatistics.parseFromGitOutput(output);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isEqualTo(5);
        assertThat(stats.getBranchesUpdated()).isEqualTo(3);
        assertThat(stats.getBranchesDeleted()).isEqualTo(2);
        assertThat(stats.getCommitsPushed()).isEqualTo(23);
    }

    @Test
    void testParseFromGitOutput_PartialFields() {
        // Given
        String output = "BRANCHES_CREATED=2\n" +
                       "COMMITS_PUSHED=10\n";

        // When
        SyncStatistics stats = SyncStatistics.parseFromGitOutput(output);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isEqualTo(2);
        assertThat(stats.getBranchesUpdated()).isNull();
        assertThat(stats.getBranchesDeleted()).isNull();
        assertThat(stats.getCommitsPushed()).isEqualTo(10);
    }

    @Test
    void testParseFromGitOutput_EmptyString() {
        // When
        SyncStatistics stats = SyncStatistics.parseFromGitOutput("");

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isEqualTo(0);
        assertThat(stats.getBranchesUpdated()).isEqualTo(0);
        assertThat(stats.getBranchesDeleted()).isEqualTo(0);
        assertThat(stats.getCommitsPushed()).isEqualTo(0);
    }

    @Test
    void testParseFromGitOutput_NullString() {
        // When
        SyncStatistics stats = SyncStatistics.parseFromGitOutput(null);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isEqualTo(0);
        assertThat(stats.getBranchesUpdated()).isEqualTo(0);
        assertThat(stats.getBranchesDeleted()).isEqualTo(0);
        assertThat(stats.getCommitsPushed()).isEqualTo(0);
    }

    @Test
    void testParseFromGitOutput_InvalidFormat() {
        // Given
        String output = "INVALID_LINE\n" +
                       "BRANCHES_CREATED=abc\n" +  // Invalid number
                       "BRANCHES_UPDATED=5\n";

        // When
        SyncStatistics stats = SyncStatistics.parseFromGitOutput(output);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isNull();  // Skipped due to parse error
        assertThat(stats.getBranchesUpdated()).isEqualTo(5);
    }

    @Test
    void testParseFromGitOutput_WithExtraData() {
        // Given - Git output with FINAL_SHA and statistics
        String output = "FINAL_SHA=abc123def456\n" +
                       "BRANCHES_CREATED=3\n" +
                       "BRANCHES_UPDATED=1\n" +
                       "COMMITS_PUSHED=8\n";

        // When
        SyncStatistics stats = SyncStatistics.parseFromGitOutput(output);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isEqualTo(3);
        assertThat(stats.getBranchesUpdated()).isEqualTo(1);
        assertThat(stats.getCommitsPushed()).isEqualTo(8);
    }

    @Test
    void testHasChanges_WithChanges() {
        // Given
        SyncStatistics stats = SyncStatistics.builder()
            .branchesCreated(2)
            .branchesUpdated(1)
            .branchesDeleted(0)
            .commitsPushed(5)
            .build();

        // Then
        assertThat(stats.hasChanges()).isTrue();
    }

    @Test
    void testHasChanges_NoChanges() {
        // Given
        SyncStatistics stats = SyncStatistics.builder()
            .branchesCreated(0)
            .branchesUpdated(0)
            .branchesDeleted(0)
            .commitsPushed(10)
            .build();

        // Then
        assertThat(stats.hasChanges()).isFalse();
    }

    @Test
    void testHasChanges_NullValues() {
        // Given
        SyncStatistics stats = SyncStatistics.builder().build();

        // Then
        assertThat(stats.hasChanges()).isFalse();
    }

    @Test
    void testGetTotalBranchChanges() {
        // Given
        SyncStatistics stats = SyncStatistics.builder()
            .branchesCreated(5)
            .branchesUpdated(3)
            .branchesDeleted(2)
            .build();

        // Then
        assertThat(stats.getTotalBranchChanges()).isEqualTo(10);
    }

    @Test
    void testGetTotalBranchChanges_NullValues() {
        // Given
        SyncStatistics stats = SyncStatistics.builder().build();

        // Then
        assertThat(stats.getTotalBranchChanges()).isEqualTo(0);
    }

    @Test
    void testEmpty() {
        // When
        SyncStatistics stats = SyncStatistics.empty();

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getBranchesCreated()).isEqualTo(0);
        assertThat(stats.getBranchesUpdated()).isEqualTo(0);
        assertThat(stats.getBranchesDeleted()).isEqualTo(0);
        assertThat(stats.getCommitsPushed()).isEqualTo(0);
        assertThat(stats.hasChanges()).isFalse();
        assertThat(stats.getTotalBranchChanges()).isEqualTo(0);
    }
}
