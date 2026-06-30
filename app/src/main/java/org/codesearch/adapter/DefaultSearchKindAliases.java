package org.codesearch.adapter;

import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DefaultSearchKindAliases {
    private static final Set<String> COMMON = Set.of(
            "class",
            "interface",
            "method",
            "field",
            "local_variable",
            "localvariable",
            "string_constant",
            "stringconstant",
            "integer_literal",
            "integerliteral",
            "float_literal",
            "floatliteral",
            "boolean_literal",
            "booleanliteral",
            "char_literal",
            "charliteral",
            "string_literal",
            "stringliteral"
    );

    private static final Set<String> TYPE_SEARCH = Set.of(
            "field-type",
            "method-return-type",
            "local-variable-type"
    );

    private DefaultSearchKindAliases() {}

    public static Set<String> contentOnly() {
        return COMMON;
    }

    public static Set<String> withDeclaredTypeSearch() {
        Set<String> aliases = new LinkedHashSet<>(COMMON);
        aliases.addAll(TYPE_SEARCH);
        return Set.copyOf(aliases);
    }

    public static Set<String> withAssignableTypeSearch() {
        Set<String> aliases = new LinkedHashSet<>(withDeclaredTypeSearch());
        aliases.add("variable-assignable-to");
        return Set.copyOf(aliases);
    }

    public static Set<String> forCapabilities(Set<SearchTarget> targets) {
        if (targets.contains(SearchTarget.ASSIGNABLE_TYPE)) {
            return withAssignableTypeSearch();
        }
        if (targets.contains(SearchTarget.DECLARED_TYPE)) {
            return withDeclaredTypeSearch();
        }
        return contentOnly();
    }
}
