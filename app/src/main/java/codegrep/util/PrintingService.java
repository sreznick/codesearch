package codegrep.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import codegrep.data.FileReference;

public class PrintingService {
    private Settings settings;
    private boolean hadError = false;

    public boolean hadError() {
        return hadError;
    }

    public PrintingService(Settings settings) {
        this.settings = settings;
    }

    public void print(String s, Object... args) {
        System.out.println(String.format(s, args));
    }

    private boolean humanReadable() {
        return !settings.onlyCount() && !settings.onlyFileNames();
    }

    public void printIfHumanReadable(String s, Object... args) {
        if (humanReadable()) {
            print(s, args);
        }
    }

    public void debug(String s, Object... args) {
        if (settings.debug()) {
            System.err.println(String.format(s, args));
        }
    }

    public void verboseDebug(String s, Object... args) {
        if (settings.verboseDebug()) {
            System.err.println(String.format(s, args));
        }
    }

    public void error(String s, Object... args) {
        System.err.println(String.format(s, args));
        hadError = true;
    }

    public void printResults(Map<Path, List<FileReference>> grepped) {
        if (!settings.quiet()) {
            doPrint(grepped);
        }
    }

    private void doPrint(Map<Path, List<FileReference>> grepped) {
        int total = grepped.values().stream().map(List::size).reduce(0, Integer::sum);
        if (humanReadable()) {
            print("Found %d references", total);
            print("====================");
            print("");
        } else if (settings.onlyCount()) {
            print("%d\ttotal", total);
        }

        boolean isFirst = true;
        for (Map.Entry<Path, List<FileReference>> entry : grepped.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else if (humanReadable()) {
                print("---");
                print("");
            }

            if (settings.onlyCount()) {
                print("%d\t%s", entry.getValue().size(), entry.getKey());
            } else if (settings.onlyFileNames()) {
                print("%s", entry.getKey());
            } else {
                print("%s - %d matches", entry.getKey(), entry.getValue().size());
                try (Stream<String> lines = Files.lines(entry.getKey())) {
                    List<String> lineList = lines.toList();
                    for (FileReference ref : entry.getValue()) {
                        for (int i = ref.startLineno(); i <= ref.endLineno(); i++) {
                            print("%d\t%s", i, lineList.get(i - 1));
                        }
                        print("");
                    }
                } catch (IOException e) {
                    error("Unable to read file %s", entry.getKey());
                    e.printStackTrace();
                }
            }
        }
    }
}
