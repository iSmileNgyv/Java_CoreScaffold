package com.ismile.core.chronovcs.dto.auth;

import com.ismile.core.chronovcs.service.auth.AuthenticatedUser;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private AuthenticatedUser user;
    private String accessToken;
    private String refreshToken;
}