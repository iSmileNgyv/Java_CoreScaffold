package com.ismile.core.chronovcs.service.versioning;

import com.ismile.core.chronovcs.entity.VersioningMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class VersioningPushStrategyRegistry {
    private final Map<VersioningMode, VersioningPushStrategy> strategies = new EnumMap<>(VersioningMode.class);

    public VersioningPushStrategyRegistry(List<VersioningPushStrategy> strategyList) {
        for(VersioningPushStrategy strategy : strategyList) {
            VersioningMode mode = strategy.getSupportedMode();
            if(strategies.containsKey(mode)) {
                throw new IllegalStateException("Duplicate VersioningPushStrategy for mode: " + mode);
            }
            strategies.put(mode, strategy);
        }
    }

    public VersioningPushStrategy getStrategy(VersioningMode mode) {
        VersioningPushStrategy strategy = strategies.get(mode);
        if(strategy == null) {
            throw new IllegalStateException("No VersioningPushStrategy for mode: " + mode);
        }
        return strategy;
    }
}
