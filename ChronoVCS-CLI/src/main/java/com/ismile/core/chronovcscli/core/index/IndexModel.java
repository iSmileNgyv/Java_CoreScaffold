package com.ismile.core.chronovcscli.core.index;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class IndexModel {
    private Map<String, String> files = new HashMap<>();
}
