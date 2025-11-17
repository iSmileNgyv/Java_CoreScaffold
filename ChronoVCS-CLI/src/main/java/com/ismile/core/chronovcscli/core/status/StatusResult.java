package com.ismile.core.chronovcscli.core.status;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class StatusResult {
    private List<String> untracked;
    private List<String> modified;
    private List<String> deleted;
}
