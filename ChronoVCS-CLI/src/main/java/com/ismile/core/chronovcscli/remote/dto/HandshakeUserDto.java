package com.ismile.core.chronovcscli.remote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HandshakeUserDto {

    private Long id;
    private String userUid;
    private String email;
}