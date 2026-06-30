package org.codesearch.adapter;

import org.codesearch.adapter.go.GoLanguageAdapter;
import org.codesearch.adapter.java.JavaLanguageAdapter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class DefaultLanguageRegistry implements LanguageRegistry {
    private final Map<String, LanguageAdapter> adaptersByLanguage;

    public DefaultLanguageRegistry(Collection<LanguageAdapter> adapters) {
        Map<String, LanguageAdapter> mapped = new LinkedHashMap<>();
        for (LanguageAdapter adapter : adapters) {
            String key = normalize(adapter.language());
            if (mapped.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate language adapter: " + key);
            }
            mapped.put(key, adapter);
        }
        this.adaptersByLanguage = Map.copyOf(mapped);
    }

    public static LanguageRegistry withDefaults() {
        return new DefaultLanguageRegistry(List.of(new JavaLanguageAdapter(), new GoLanguageAdapter()));
    }

    @Override
    public Optional<LanguageAdapter> find(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adaptersByLanguage.get(normalize(language)));
    }

    @Override
    public LanguageAdapter require(String language) {
        return find(language).orElseThrow(() ->
                new IllegalArgumentException("Unsupported language: " + language)
        );
    }

    @Override
    public Collection<LanguageAdapter> all() {
        return adaptersByLanguage.values();
    }

    @Override
    public Optional<LanguageAdapter> detectByFile(String filePath) {
        return all().stream()
                .filter(adapter -> adapter.supportsFile(filePath))
                .findFirst();
    }

    private static String normalize(String language) {
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
