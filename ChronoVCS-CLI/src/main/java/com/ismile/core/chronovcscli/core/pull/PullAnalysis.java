package com.ismile.core.chronovcscli.core.pull;

import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullAnalysis {

    public enum PullStrategy {
        UP_TO_DATE,      // Local and remote are same
        FAST_FORWARD,    // Remote is ahead, can fast-forward
        DIVERGED,        // Both have new commits, manual merge needed
        LOCAL_AHEAD      // Local is ahead of remote
    }

    private PullStrategy strategy;
    private String localHead;
    private String remoteHead;
    private List<CommitSnapshotDto> newCommits;
    private String message;
}
