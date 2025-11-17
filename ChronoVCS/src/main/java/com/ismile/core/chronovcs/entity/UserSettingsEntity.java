package com.ismile.core.chronovcs.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_settings")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_uid", nullable = false, unique = true)
    private String userUid;
    @Column(length = 100)
    private String email;
    @Column(name = "core_default_branch")
    private String coreDefaultBranch;
    @Column(name = "cli_alias")
    private String cliAlias;
}
