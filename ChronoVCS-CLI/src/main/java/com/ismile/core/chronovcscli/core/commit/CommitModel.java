package com.ismile.core.chronovcscli.core.commit;

import lombok.Data;

import java.util.Map;

@Data
public class CommitModel {
    private String id;
    private String parent;
    private String message;
    private String timestamp;
    private Map<String, String> files; // filename -> blobHash
}
