package org.codesearch.engine;

import org.codesearch.go.GoLanguagePlugin;
import org.codesearch.java.JavaLanguagePlugin;
import org.codesearch.plugin.LanguagePlugin;
import org.codesearch.python.PythonLanguagePlugin;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LanguageRegistry {
    private final Map<String, LanguagePlugin> pluginsByLanguage;

    public LanguageRegistry(Collection<? extends LanguagePlugin> plugins) {
        Map<String, LanguagePlugin> mapped = new LinkedHashMap<>();
        for (LanguagePlugin plugin : plugins) {
            String key = normalize(plugin.language());
            if (mapped.putIfAbsent(key, plugin) != null) {
                throw new IllegalArgumentException("Duplicate language plugin: " + key);
            }
        }
        this.pluginsByLanguage = java.util.Collections.unmodifiableMap(mapped);
    }

    public static LanguageRegistry withDefaults() {
        return new LanguageRegistry(List.of(
                new JavaLanguagePlugin(),
                new GoLanguagePlugin(),
                new PythonLanguagePlugin()
        ));
    }

    public Optional<LanguagePlugin> find(String language) {
        if (language == null || language.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(pluginsByLanguage.get(normalize(language)));
    }

    public LanguagePlugin require(String language) {
        return find(language).orElseThrow(() ->
                new IllegalArgumentException("Неподдерживаемый язык: " + language));
    }

    public boolean supports(String language) {
        return find(language).isPresent();
    }

    public Collection<LanguagePlugin> all() {
        return pluginsByLanguage.values();
    }

    public List<String> languages() {
        return List.copyOf(pluginsByLanguage.keySet());
    }

    private static String normalize(String language) {
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
