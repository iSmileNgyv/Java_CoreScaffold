package com.ismile.core.chronovcs.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepoPermissionResponseDto {
    private String repoKey;
    private String userUid;
    private String userEmail;
    private boolean owner;
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
