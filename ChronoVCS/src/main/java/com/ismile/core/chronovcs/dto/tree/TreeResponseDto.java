package com.ismile.core.chronovcs.dto.tree;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeResponseDto {
    private String repoKey;
    private String ref;
    private String commitId;
    private String path;
    private List<TreeEntryDto> entries;
}
