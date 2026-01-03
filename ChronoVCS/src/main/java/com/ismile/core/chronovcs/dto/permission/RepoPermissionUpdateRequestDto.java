package com.ismile.core.chronovcs.dto.permission;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RepoPermissionUpdateRequestDto {
    private String userEmail;
    private String userUid;

    @NotNull
    private Boolean canRead;
    @NotNull
    private Boolean canPull;
    @NotNull
    private Boolean canPush;
    @NotNull
    private Boolean canCreateBranch;
    @NotNull
    private Boolean canDeleteBranch;
    @NotNull
    private Boolean canMerge;
    @NotNull
    private Boolean canCreateTag;
    @NotNull
    private Boolean canDeleteTag;
    @NotNull
    private Boolean canManageRepo;
    @NotNull
    private Boolean canBypassTaskPolicy;
}
