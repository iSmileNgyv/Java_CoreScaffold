package com.ismile.core.chronovcs.dto.permission;

import com.ismile.core.chronovcs.dto.handshake.HandshakePermissionDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EffectivePermissionResponseDto {
    private String source; // TOKEN_OVERRIDE, USER_REPO, OWNER
    private Long tokenId; // present only for PAT-authenticated requests
    private HandshakePermissionDto permissions;
}
