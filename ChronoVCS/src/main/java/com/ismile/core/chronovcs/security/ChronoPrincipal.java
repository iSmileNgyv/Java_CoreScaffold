package com.ismile.core.chronovcs.security;

import com.ismile.core.chronovcs.entity.UserEntity;
import lombok.Value;

@Value
public class ChronoPrincipal {
    Long userId;
    String userUid;
    String email;

    public static ChronoPrincipal fromEntity(UserEntity user) {
        return new ChronoPrincipal(
                user.getId(),
                user.getUserUid(),
                user.getEmail()
        );
    }
}
