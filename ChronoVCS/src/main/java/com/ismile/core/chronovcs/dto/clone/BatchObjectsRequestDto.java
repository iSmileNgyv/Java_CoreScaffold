package com.ismile.core.chronovcs.dto.clone;

import lombok.Data;

import java.util.List;

@Data
public class BatchObjectsRequestDto {
    /**
     * List of blob hashes to retrieve
     */
    private List<String> hashes;
}
