package codegrep;

import codegrep.data.DocumentCacheManager;
import codegrep.data.FileReference;
import codegrep.util.PrintingService;
import codegrep.util.Settings;
import grammars.CodeGrepGrammarFacade;
import grammars.python3.facade.Python3Facade;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Main {
    private static CodeGrepGrammarFacade grammar;
    public static PrintingService printer;

    public static void main(String[] args) {
        Settings settings = new Settings(args);
        String searchString = settings.searchString();
        if (searchString == null) return;  // failed to parse arguments, --help used, etc.
        printer = new PrintingService(settings);

        Path cwd = Path.of(System.getProperty("user.dir"));
        grammar = new Python3Facade();
        DocumentCacheManager dcm = new DocumentCacheManager(settings);
        dcm.revalidate(cwd, grammar);

        Map<Path, List<FileReference>> grepped = dcm.search(searchString);
        printer.printResults(grepped);
        if (printer.hadError()) System.exit(2);  // FS access failed, etc
        else if (grepped.isEmpty()) System.exit(1);  // nothing found
    }
}
