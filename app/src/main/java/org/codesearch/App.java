package org.codesearch;

import org.codesearch.cli.CodeSearchCli;
import org.codesearch.engine.LanguageRegistry;

import java.io.PrintStream;
import java.nio.file.Path;

public class App {
    private static final Path DEFAULT_INDEX_PATH = Path.of("index");

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, DEFAULT_INDEX_PATH);
    }

    static int run(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        return new CodeSearchCli(LanguageRegistry.withDefaults(), indexPath, out, err).run(args);
    }
}
