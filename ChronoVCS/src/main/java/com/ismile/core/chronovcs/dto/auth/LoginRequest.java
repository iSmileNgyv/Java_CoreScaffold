package com.ismile.core.chronovcs.dto.auth;

import lombok.Data;

@Data
public class LoginRequest {

    /**
     * User email (we use email as login name).
     */
    private String email;

    /**
     * User token / password equivalent.
     * (Bizdə hazırda tokenHash var, onu istifadə edirik.)
     */
    private String token;
}