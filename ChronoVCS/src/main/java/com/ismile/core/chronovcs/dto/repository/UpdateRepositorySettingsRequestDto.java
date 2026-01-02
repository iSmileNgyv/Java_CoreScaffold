package com.ismile.core.chronovcs.dto.repository;

import lombok.Data;

@Data
public class UpdateRepositorySettingsRequestDto {
    private Boolean releaseEnabled;
    private Boolean taskRequired;
    private Boolean autoIncrement;
    private Boolean enforceSemanticVersioning;
}
