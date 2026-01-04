package com.ismile.core.chronovcs.dto.pullrequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestListResponse {
    private List<PullRequestResponseDto> pullRequests;
    private int totalCount;
}
