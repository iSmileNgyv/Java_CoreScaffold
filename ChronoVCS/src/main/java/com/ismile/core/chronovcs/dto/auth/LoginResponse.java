package com.ismile.core.chronovcs.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String email;
    private String userUid;
}