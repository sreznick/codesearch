package org.codesearch.adapter.java;

import org.codesearch.adapter.PluginLanguageAdapter;
import org.codesearch.java.JavaLanguagePlugin;


public final class JavaLanguageAdapter extends PluginLanguageAdapter {
    public JavaLanguageAdapter() {
        super(new JavaLanguagePlugin());
    }
}
