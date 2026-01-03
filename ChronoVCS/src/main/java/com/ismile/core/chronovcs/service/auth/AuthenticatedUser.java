package com.ismile.core.chronovcs.service.auth;

import com.ismile.core.chronovcs.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser {
    private Long userId;
    private String userUid;
    private String email;
    private Long tokenId;

    public static AuthenticatedUser fromEntity(UserEntity user) {
        return AuthenticatedUser.builder()
                .userId(user.getId())
                .userUid(user.getUserUid())
                .email(user.getEmail())
                .build();
    }
}
