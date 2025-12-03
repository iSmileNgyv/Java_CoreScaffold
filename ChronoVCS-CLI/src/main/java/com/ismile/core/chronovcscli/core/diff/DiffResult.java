package com.ismile.core.chronovcscli.core.diff;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DiffResult {
    private List<FileDiff> fileDiffs;
}
