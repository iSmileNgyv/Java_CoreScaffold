package com.ismile.core.chronovcscli.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandshakeResponseDto {

    private boolean success;
    private HandshakeUserDto user;
    private HandshakeRepositoryDto repository;
    private HandshakePermissionDto permissions;
}