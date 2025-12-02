package com.ismile.core.chronovcs.dto.clone;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchObjectsResponseDto {
    /**
     * Map of blob hash -> base64 encoded content
     */
    private Map<String, String> objects;
}
