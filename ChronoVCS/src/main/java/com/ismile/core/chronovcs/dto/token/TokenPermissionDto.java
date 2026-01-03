package com.ismile.core.chronovcs.dto.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPermissionDto {
    private String repoKey;
    private boolean canRead;
    private boolean canPull;
    private boolean canPush;
    private boolean canCreateBranch;
    private boolean canDeleteBranch;
    private boolean canMerge;
    private boolean canCreateTag;
    private boolean canDeleteTag;
    private boolean canManageRepo;
    private boolean canBypassTaskPolicy;
}
