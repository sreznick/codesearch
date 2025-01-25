package codegrep.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

public class FileUtils {
    
    public static void processFiles(Path p, @Nonnull Predicate<Path> filter, @Nonnull Consumer<Path> cons) {
        if (p.toFile().isDirectory()) {
            try (Stream<Path> walk = Files.walk(p)) {
                walk.filter(e -> !e.toFile().isDirectory()).filter(filter).forEach(f -> processFiles(f, filter, cons));
            } catch (IOException e) {
                System.err.println("Unable to read the directory");
                e.printStackTrace();
            }
            return;
        }

        if (filter.test(p)) {
            cons.accept(p);
        }
    }
}
