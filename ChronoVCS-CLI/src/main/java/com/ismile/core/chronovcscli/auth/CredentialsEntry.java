package com.ismile.core.chronovcscli.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CredentialsEntry {
    private String baseUrl;
    private String userUid;
    private String email;
    private String token;
}
