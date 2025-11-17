package com.ismile.core.chronovcscli.core.index;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IndexEntry {
    private String path;
    private String hash;
}
