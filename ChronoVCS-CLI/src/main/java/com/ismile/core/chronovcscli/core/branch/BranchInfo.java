package com.ismile.core.chronovcscli.core.branch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchInfo {
    private String name;
    private String commitHash;
    private boolean isCurrent;
}
