package org.codesearch.cli;

final class Ansi {
    static final String GREEN = "\u001B[32m";
    static final String BLUE = "\u001B[34m";
    static final String YELLOW = "\u001B[33m";
    static final String RED = "\u001B[31m";
    static final String DIM = "\u001B[2m";
    private static final String RESET = "\u001B[0m";
    private static final boolean ENABLED = detect();

    private Ansi() {}

    static String color(String color, String text) {
        return ENABLED ? color + text + RESET : text;
    }

    private static boolean detect() {
        if (System.getenv("NO_COLOR") != null || System.console() == null) {
            return false;
        }
        String term = System.getenv("TERM");
        return term != null && !"dumb".equalsIgnoreCase(term);
    }
}
