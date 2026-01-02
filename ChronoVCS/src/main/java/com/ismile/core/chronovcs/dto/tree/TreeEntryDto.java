package com.ismile.core.chronovcs.dto.tree;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TreeEntryDto {
    private String name;
    private String path;
    private String type; // DIR or FILE
    private String blobHash; // only for FILE
    private String url; // only for FILE
}
