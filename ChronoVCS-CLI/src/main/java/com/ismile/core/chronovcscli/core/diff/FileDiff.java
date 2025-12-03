package com.ismile.core.chronovcscli.core.diff;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FileDiff {
    private String filePath;
    private ChangeType changeType;
    private List<String> hunks;

    public enum ChangeType {
        ADDED, MODIFIED, DELETED
    }
}
