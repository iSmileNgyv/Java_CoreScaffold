package com.ismile.core.chronovcs.dto.auth;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}