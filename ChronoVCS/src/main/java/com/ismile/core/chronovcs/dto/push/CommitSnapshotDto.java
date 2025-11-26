package com.ismile.core.chronovcs.dto.push;

import lombok.Data;
import java.util.Map;

@Data
public class CommitSnapshotDto {
    private String id;
    private String parent;
    private String authorUid;
    private String branch;
    private String message;
    private String timestamp;
    /**
     * filename -> blobHash
     */
    private Map<String, String> files;
}
