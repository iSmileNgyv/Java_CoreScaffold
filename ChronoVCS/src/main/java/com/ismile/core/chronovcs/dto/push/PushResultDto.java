package com.ismile.core.chronovcs.dto.push;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PushResultDto {
    private String branch;
    private String newHeadCommitId;
    private boolean fastForward;
}
