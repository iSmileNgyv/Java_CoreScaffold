package com.ismile.core.chronovcscli.core.pull;

import com.ismile.core.chronovcscli.remote.dto.CommitSnapshotDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommitComparator {

    private final LocalCommitReader localCommitReader;

    /**
     * Analyze pull operation - compare local and remote commits
     */
    public PullAnalysis analyzePull(File projectRoot,
                                     String localHead,
                                     String remoteHead,
                                     List<CommitSnapshotDto> remoteCommits) {

        // Case 1: Same HEAD - up to date
        if (localHead != null && localHead.equals(remoteHead)) {
            return PullAnalysis.builder()
                    .strategy(PullAnalysis.PullStrategy.UP_TO_DATE)
                    .localHead(localHead)
                    .remoteHead(remoteHead)
                    .message("Already up to date")
                    .build();
        }

        // Case 2: No local commits - first pull
        if (localHead == null || localHead.isEmpty()) {
            return PullAnalysis.builder()
                    .strategy(PullAnalysis.PullStrategy.FAST_FORWARD)
                    .localHead(localHead)
                    .remoteHead(remoteHead)
                    .newCommits(remoteCommits)
                    .message("First pull - downloading " + remoteCommits.size() + " commits")
                    .build();
        }

        // Build set of local commit hashes
        Set<String> localCommitHashes = new HashSet<>();
        List<CommitSnapshotDto> localCommits = localCommitReader.readCommitChain(projectRoot, localHead, 1000);
        for (CommitSnapshotDto commit : localCommits) {
            localCommitHashes.add(commit.getId());
        }

        // Check if remote HEAD exists in local history
        boolean remoteHeadInLocalHistory = localCommitHashes.contains(remoteHead);

        if (remoteHeadInLocalHistory) {
            // Remote is behind local
            return PullAnalysis.builder()
                    .strategy(PullAnalysis.PullStrategy.LOCAL_AHEAD)
                    .localHead(localHead)
                    .remoteHead(remoteHead)
                    .message("Local is ahead of remote - push your changes")
                    .build();
        }

        // Build set of remote commit hashes
        Set<String> remoteCommitHashes = new HashSet<>();
        for (CommitSnapshotDto commit : remoteCommits) {
            remoteCommitHashes.add(commit.getId());
        }

        // Check if local HEAD exists in remote history
        boolean localHeadInRemoteHistory = remoteCommitHashes.contains(localHead);

        if (localHeadInRemoteHistory) {
            // Fast-forward possible: remote is ahead
            // Find new commits (from remote HEAD back to local HEAD)
            List<CommitSnapshotDto> newCommits = new ArrayList<>();
            for (CommitSnapshotDto commit : remoteCommits) {
                if (commit.getId().equals(localHead)) {
                    break;
                }
                newCommits.add(commit);
            }

            return PullAnalysis.builder()
                    .strategy(PullAnalysis.PullStrategy.FAST_FORWARD)
                    .localHead(localHead)
                    .remoteHead(remoteHead)
                    .newCommits(newCommits)
                    .message("Fast-forward: " + newCommits.size() + " new commits")
                    .build();
        }

        // Case 4: Diverged - both have new commits
        return PullAnalysis.builder()
                .strategy(PullAnalysis.PullStrategy.DIVERGED)
                .localHead(localHead)
                .remoteHead(remoteHead)
                .message("Local and remote have diverged - manual merge needed")
                .build();
    }
}
