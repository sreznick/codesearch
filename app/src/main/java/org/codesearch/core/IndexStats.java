package org.codesearch.core;

import java.util.Map;

public record IndexStats(long totalEntities, long totalFiles, Map<EntityKind, Long> entitiesByKind) {
    public IndexStats {
        entitiesByKind = entitiesByKind == null ? Map.of() : Map.copyOf(entitiesByKind);
    }
}
