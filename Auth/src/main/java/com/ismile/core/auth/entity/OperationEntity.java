package com.ismile.core.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.Set;

@Entity
@Data
@Table(name = "operation")
public class OperationEntity {
    @Id
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String endpoint;
    @Column(nullable = false, name = "is_global")
    private boolean isGlobal;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RoleAccessEntity> roleAccessEntitySet;
}
