package com.ismile.core.chronovcs.dto.repository;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RepositorySettingsResponseDto {
    private boolean releaseEnabled;
    private boolean taskRequired;
    private boolean autoIncrement;
    private boolean enforceSemanticVersioning;
}
