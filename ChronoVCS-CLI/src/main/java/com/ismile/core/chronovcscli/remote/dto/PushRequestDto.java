package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PushRequestDto {
    private String branch;
    private String baseCommitId;
    /**
     * Local HEAD commit snapshot (optional ID)
     */
    private CommitSnapshotDto newCommit;
    /**
     * blobHash -> base64 content
     */
    private Map<String, String> blobs;
}
