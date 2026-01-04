package com.ismile.core.chronovcs.dto.pullrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestResponseDto {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String sourceBranch;
    private String targetBranch;
    private String sourceHeadCommitId;
    private String targetHeadCommitId;
    private String mergeCommitId;
    private String createdBy;
    private String mergedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private LocalDateTime mergedAt;
}
