package com.ismile.core.chronovcscli.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandshakePermissionDto {

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