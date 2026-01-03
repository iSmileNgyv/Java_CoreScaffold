package com.ismile.core.chronovcs.dto.token;

import com.ismile.core.chronovcs.dto.handshake.HandshakePermissionDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CliTokenResponseDto {
    private String repoKey;
    private TokenResponse token;
    private HandshakePermissionDto permissions;
}
