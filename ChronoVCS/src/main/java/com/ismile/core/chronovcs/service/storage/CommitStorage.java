package com.ismile.core.chronovcs.service.storage;

import com.ismile.core.chronovcs.dto.push.CommitSnapshotDto;
import com.ismile.core.chronovcs.entity.RepositoryEntity;

public interface CommitStorage {
    void saveCommit(RepositoryEntity repo, String branch, CommitSnapshotDto commit);
    String getBranchHead(RepositoryEntity repo, String branch);
    void updateBranchHead(RepositoryEntity repo, String branch, String commitId);
}