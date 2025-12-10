package com.ismile.core.chronovcscli.core.merge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Three-way merge algorithm for file content
 */
@Slf4j
@Component
public class ThreeWayMerge {

    /**
     * Result of a three-way merge attempt
     */
    public static class MergeAttempt {
        public final boolean hasConflict;
        public final byte[] mergedContent;

        public MergeAttempt(boolean hasConflict, byte[] mergedContent) {
            this.hasConflict = hasConflict;
            this.mergedContent = mergedContent;
        }

        public static MergeAttempt success(byte[] content) {
            return new MergeAttempt(false, content);
        }

        public static MergeAttempt conflict() {
            return new MergeAttempt(true, null);
        }
    }

    /**
     * Attempt to merge three versions of a file
     *
     * @param baseContent   content from common ancestor
     * @param localContent  content from local branch
     * @param remoteContent content from remote branch
     * @return merge attempt result
     */
    public MergeAttempt merge(byte[] baseContent, byte[] localContent, byte[] remoteContent) {

        // If local and remote are identical, no conflict
        if (Arrays.equals(localContent, remoteContent)) {
            log.debug("No changes between local and remote");
            return MergeAttempt.success(localContent);
        }

        // If local unchanged, take remote
        if (Arrays.equals(baseContent, localContent)) {
            log.debug("Local unchanged, taking remote version");
            return MergeAttempt.success(remoteContent);
        }

        // If remote unchanged, keep local
        if (Arrays.equals(baseContent, remoteContent)) {
            log.debug("Remote unchanged, keeping local version");
            return MergeAttempt.success(localContent);
        }

        // Both changed differently - conflict
        log.debug("Both local and remote changed - conflict detected");
        return MergeAttempt.conflict();
    }

    /**
     * Simplified line-by-line merge (basic implementation)
     * Can be extended for more sophisticated merging
     */
    public MergeAttempt mergeLineByLine(byte[] baseContent, byte[] localContent, byte[] remoteContent) {
        String base = new String(baseContent);
        String local = new String(localContent);
        String remote = new String(remoteContent);

        String[] baseLines = base.split("\n", -1);
        String[] localLines = local.split("\n", -1);
        String[] remoteLines = remote.split("\n", -1);

        // Simple check: if line counts differ significantly, it's complex
        if (Math.abs(localLines.length - remoteLines.length) > 10) {
            return MergeAttempt.conflict();
        }

        // For now, use simple binary merge
        // A more sophisticated implementation would do line-by-line diff
        return merge(baseContent, localContent, remoteContent);
    }
}
