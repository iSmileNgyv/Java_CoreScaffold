package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CommitSnapshotDto {
    private String id;
    private String parent;
    private String message;
    private String timestamp;
    /**
     * filename -> blobHash
     */
    private Map<String, String> files;
}
