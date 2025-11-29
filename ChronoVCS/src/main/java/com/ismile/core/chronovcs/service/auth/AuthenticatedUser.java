package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.entity.UserEntity;
import lombok.Value;

@Value
public class AuthenticatedUser {
    Long userId;
    String userUid;
    String email;

    public static AuthenticatedUser fromEntity(UserEntity user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getUserUid(),
                user.getEmail()
        );
    }
}