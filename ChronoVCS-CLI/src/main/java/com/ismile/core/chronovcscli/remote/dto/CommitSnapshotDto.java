package com.ismile.core.chronovcscli.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
