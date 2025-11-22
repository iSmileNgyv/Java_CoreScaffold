package com.ismile.core.chronovcs.dto.handshake;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HandshakePermissionDto {
    boolean canRead;
    boolean canPull;
    boolean canPush;
    boolean canCreateBranch;
    boolean canDeleteBranch;
    boolean canMerge;
    boolean canCreateTag;
    boolean canDeleteTag;
    boolean canManageRepo;
    boolean canBypassTaskPolicy;
}
