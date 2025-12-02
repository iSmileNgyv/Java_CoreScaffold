package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;
import java.util.Map;

@Data
public class RefsResponseDto {
    private String defaultBranch;
    private Map<String, String> branches;
}
