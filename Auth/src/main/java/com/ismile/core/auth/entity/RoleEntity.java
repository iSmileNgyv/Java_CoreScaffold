package com.ismile.core.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Collection;
import java.util.Set;

@Entity
@Data
@Table(name = "role")
public class RoleEntity {
    @Id
    private String code;
    private String name;
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE", name = "default_role")
    private boolean defaultRole = false;
    @ManyToMany(mappedBy = "roles")
    private Collection<UserEntity> users;
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RoleAccessEntity> roleAccessEntitySet;
}
