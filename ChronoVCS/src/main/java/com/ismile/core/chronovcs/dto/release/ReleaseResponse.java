package com.ismile.core.chronovcs.dto.release;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseResponse {
    private Long id;
    private String version;
    private String versionType;
    private String message;
    private String snapshotCommitId;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<ReleaseTaskDto> tasks;
}
