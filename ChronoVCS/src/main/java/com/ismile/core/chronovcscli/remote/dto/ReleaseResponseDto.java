package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;

@Data
public class ReleaseResponseDto {
    private Long id;
    private String version;
    private String versionType;
    private String message;
    private String snapshotCommitId;
}
