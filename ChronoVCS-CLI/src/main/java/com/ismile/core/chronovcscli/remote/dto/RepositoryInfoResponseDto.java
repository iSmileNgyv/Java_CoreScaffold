package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;

@Data
public class RepositoryInfoResponseDto {
    private String defaultBranch;
    private boolean releaseEnabled;
}
