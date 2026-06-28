package org.codesearch.go;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GoEntityExtractorTest {
    @Test
    void shouldNotPrintAntlrErrorsToSystemErr(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("broken.go");
        Files.writeString(file, """
                package demo

                func broken() {
                    _ = )
                }
                """);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr));
        try {
            new GoEntityExtractor().extractEntities(file);
        } catch (RuntimeException ignored) {
            // The extractor may fail on broken Go code, but ANTLR must not write directly to stderr.
        } finally {
            System.setErr(originalErr);
        }

        assertFalse(capturedErr.toString().contains("mismatched input"));
    }
}
