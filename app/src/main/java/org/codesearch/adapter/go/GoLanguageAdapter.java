package org.codesearch.adapter.go;

import org.codesearch.adapter.PluginLanguageAdapter;
import org.codesearch.go.GoLanguagePlugin;

public final class GoLanguageAdapter extends PluginLanguageAdapter {
    public GoLanguageAdapter() {
        super(new GoLanguagePlugin());
    }
}
