package com.ismile.core.chronovcscli.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReleaseResponseDto {
    private Long id;
    private String version;
    private String versionType;
    private String message;
    private String snapshotCommitId;
    private String createdBy;
    private String createdAt;
    private List<ReleaseTaskDto> tasks;
}
