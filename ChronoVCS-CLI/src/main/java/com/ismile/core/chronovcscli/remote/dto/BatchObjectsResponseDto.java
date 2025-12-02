package com.ismile.core.chronovcscli.remote.dto;

import lombok.Data;
import java.util.Map;

@Data
public class BatchObjectsResponseDto {
    private Map<String, String> objects;
}
