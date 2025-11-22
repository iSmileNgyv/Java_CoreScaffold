package com.ismile.core.chronovcs.dto.handshake;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HandshakeUserDto {
    Long id;
    String userUid;
    String email;
}
