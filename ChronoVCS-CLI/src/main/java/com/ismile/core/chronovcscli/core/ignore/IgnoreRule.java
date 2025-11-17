package com.ismile.core.chronovcscli.core.ignore;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IgnoreRule {
    private String pattern;
    private boolean isDirectoryRule;
}
