package codegrep;

import codegrep.data.FileReference;
import codegrep.data.ProjectContents;
import grammars.CodeGrepGrammarFacade;
import grammars.ExtendedParseTreeListener;
import grammars.python3.facade.Python3Facade;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static ExtendedParseTreeListener listener;
    private static CodeGrepGrammarFacade grammar;

    private static void parseFile(Path p) {
        if (p.toFile().isDirectory()) {
            try (Stream<Path> walk = Files.walk(p)) {
                walk.filter(grammar::isValidFile).forEach(Main::parseFile);
            } catch (IOException e) {
                System.out.println("Unable to read the directory");
                e.printStackTrace();
            }
            return;
        }

        listener.setPath(p);
        try {
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, grammar.parse(p.toFile()));
        } catch (IOException e) {
            System.out.printf("Unable to read the file %s%n", p);
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java Main <string to find> <file or folder>");
            return;
        }

        File input = new File(args[1]);
        if (!input.exists()) {
            System.out.println("File not found: " + args[1]);
            return;
        }

        grammar = new Python3Facade();
        listener = grammar.createListener();
        parseFile(input.toPath());

        ProjectContents contents = listener.getProjectContents();
        Map<Path, List<FileReference>> grepped = contents.getLiterals(args[0]);
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
