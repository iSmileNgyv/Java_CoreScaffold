package com.ismile.core.chronovcscli.core.commit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitModel {
    private String id;
    private String parent;
    private String mergeParent;  // Second parent for merge commits
    private String message;
    private String timestamp;
    private Map<String, String> files; // filename -> blobHash

    // Legacy compatibility getters/setters
    public String getParentCommitId() {
        return parent;
    }

    public void setParentCommitId(String parentCommitId) {
        this.parent = parentCommitId;
    }

    public String getMergeParentCommitId() {
        return mergeParent;
    }

    public void setMergeParentCommitId(String mergeParentCommitId) {
        this.mergeParent = mergeParentCommitId;
    }
}
