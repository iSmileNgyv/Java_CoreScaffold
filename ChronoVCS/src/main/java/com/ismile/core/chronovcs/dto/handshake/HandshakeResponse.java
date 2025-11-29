package com.ismile.core.chronovcs.dto.handshake;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HandshakeResponse {
    boolean success;
    HandshakeUserDto user;
    HandshakeRepositoryDto repository;
    HandshakePermissionDto permissions;
}
