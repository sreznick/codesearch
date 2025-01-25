package codegrep;

import codegrep.data.DocumentCacheManager;
import codegrep.data.FileReference;
import grammars.CodeGrepGrammarFacade;
import grammars.python3.facade.Python3Facade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static CodeGrepGrammarFacade grammar;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Main <string to find>");
            return;
        }

        Path cwd = Path.of(System.getProperty("user.dir"));
        grammar = new Python3Facade();
        DocumentCacheManager dcm = new DocumentCacheManager();
        dcm.revalidate(cwd, grammar);

        Map<Path, List<FileReference>> grepped = dcm.search(args[0]);
        System.out.printf("Found %d references%n", grepped.values().stream().map(List::size).reduce(0, Integer::sum));
        System.out.println("====================");
        System.out.println();

        boolean isFirst = true;
        for (Map.Entry<Path, List<FileReference>> entry : grepped.entrySet()) {
            // This is ugly, but file system access sucks.
            if (isFirst) {
                isFirst = false;
            } else {
                System.out.println("---");
                System.out.println();
            }

            System.out.printf("%s - %d matches%n", entry.getKey(), entry.getValue().size());
            try (Stream<String> lines = Files.lines(entry.getKey())) {
                List<String> lineList = lines.toList();
                for (FileReference ref : entry.getValue()) {
                    for (int i = ref.startLineno(); i <= ref.endLineno(); i++) {
                        System.out.printf("%d\t%s%n", i, lineList.get(i - 1));
                    }
                    System.out.println();
                }
            } catch (IOException e) {
                System.out.println("Unable to read the file :(");
                e.printStackTrace();
            }
        }
    }
}
