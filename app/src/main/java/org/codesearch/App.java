package org.codesearch;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.*;

import org.codesearch.CodeSearch.*;
import org.codesearch.Units.Unit;

public class App {
    public static void main(String[] args) {
        MainParams mainParams = new MainParams();
        JCommander mainJC = new JCommander(mainParams);
        mainParams.setCommands(mainJC);
        try {
            mainJC.parse(args);
            mainParams.run(mainJC);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            mainJC.usage();
        }
    }

    @Parameters(commandDescription = "CodeSearch")
    public static class MainParams {
        @Parameter(names = {"-h", "--help"}, help = true)
        private boolean help;

        public GolangParams golangParams;

        MainParams() {
            golangParams = new GolangParams();
        }

        public void setCommands(JCommander mainJC) {
            mainJC.addCommand("golang", golangParams);
            golangParams.setCommands(mainJC.getCommands().get("golang"));
        }

        public void run(JCommander mainJC) {
            String command = mainJC.getParsedCommand();
            if (help || command == null) {
                mainJC.usage();
                return;
            }
            switch (command) {
                case "golang":
                    golangParams.run(mainJC.getCommands().get(command)); break;
            }
        }
    }
    
    @Parameters(commandNames = {"golang"}, commandDescription = "CodeSearch for golang")
    public static class GolangParams {
        @Parameter(names = {"-h", "--help"}, help = true)
        private boolean help;

        public IndexParams indexParams;
        public SearchParams searchParams;

        @Parameter(names = {"-i", "--index_path"}, description = "index dir", required = true)
        public String indexDirPath;

        GolangParams() {
            indexParams = new IndexParams();
            searchParams = new SearchParams();
        }

        @Parameters(commandNames = {"index"}, commandDescription = "Dir indexing")
        public static class IndexParams {
            @Parameter(names = {"-h", "--help"}, help = true)
            private boolean help;

            @Parameter(names = {"-s", "--source"}, description = "source dir", required = true)
            public String sourceDirPath;

            public void run(JCommander indexJC, CodeSearcher cs) {
                if (help) {
                    indexJC.usage();
                    return;
                }
                try {
                    cs.buildIndex(sourceDirPath);   
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }

        @Parameters(commandNames = {"search"}, commandDescription = "Search units")
        public static class SearchParams {
            @Parameter(names = {"-h", "--help"}, help = true)
            private boolean help;

            @Parameter(names = {"-f", "--is_fuzzy"}, description = "fuzzy search")
            public boolean isFuzzy;

            @Parameter(names = {"-t", "--num_top_docs"}, description = "num top docs in search")
            public int numTopDocs = 100;

            @Parameter(description = "keys for search", required = true)
            public List<String> keys = new ArrayList<>();

            public void run(JCommander searchJC, CodeSearcher cs) {
                if (help) {
                    searchJC.usage();
                    return;
                }
                try {
                    List<Unit> units = cs.codeSearch(keys, isFuzzy, numTopDocs);
                    System.out.println(String.format("Num of docs: %d", units.size()));
                    for (int i = 0; i < units.size(); ++i) {
                        System.out.println(String.format("%d\t| [%s]", i+1, units.get(i).toString()));
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }

        public void setCommands(JCommander golangJC) {
            golangJC.addCommand("index", indexParams);
            golangJC.addCommand("search", searchParams);
        }

        public void run(JCommander golangJC) {
            String command = golangJC.getParsedCommand();
            if (help || command == null) {
                golangJC.usage();
                return;
            }
            CodeSearcher cs = new GolangCodeSearcher(indexDirPath);
            JCommander jc = golangJC.getCommands().get(command);
            switch (command) {
                case "index":
                    indexParams.run(jc, cs); break;
                case "search":
                    searchParams.run(jc, cs); break;
            }
        }
    }
}
