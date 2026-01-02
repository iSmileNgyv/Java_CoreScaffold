package com.ismile.core.chronovcs.dto.push;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitFileEntryDto {
    private String path;
    private String blobHash;
    private String url;
}
