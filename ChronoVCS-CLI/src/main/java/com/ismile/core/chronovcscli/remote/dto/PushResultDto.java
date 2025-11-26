package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;

@Data
public class PushResultDto {
    private String branch;
    private String newHeadCommitId;
    private boolean fastForward;
}
