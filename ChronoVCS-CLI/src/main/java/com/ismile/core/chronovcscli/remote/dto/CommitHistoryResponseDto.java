package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;
import java.util.List;

@Data
public class CommitHistoryResponseDto {
    private List<CommitSnapshotDto> commits;
    private boolean hasMore;
}
