package com.ismile.core.chronovcs.dto.clone;

import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitHistoryResponseDto {
    /**
     * List of commits in reverse chronological order (newest first)
     */
    private List<CommitSnapshotDto> commits;

    /**
     * Indicates if there are more commits available
     */
    private boolean hasMore;
}
