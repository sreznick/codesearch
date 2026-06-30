package org.codesearch.core;

import java.util.Collections;
import java.util.Map;

public final class IndexingContext {
    public static final IndexingContext EMPTY = new IndexingContext(Map.of());

    private final Map<Class<?>, Object> values;

    public IndexingContext(Map<Class<?>, Object> values) {
        this.values = values == null || values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    public <T> T get(Class<T> type) {
        Object value = values.get(type);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    public Map<Class<?>, Object> values() {
        return Collections.unmodifiableMap(values);
    }
}
