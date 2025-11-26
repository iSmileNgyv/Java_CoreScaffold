package com.ismile.core.chronovcs.dto.push;

import lombok.Data;

import java.util.Map;

@Data
public class PushRequestDto {
    private String branch;
    private String baseCommitId;
    private CommitSnapshotDto  newCommit;
    /**
     * Map of blobHash -> base64-encoded content
     */
    private Map<String, String> blobs;
}
