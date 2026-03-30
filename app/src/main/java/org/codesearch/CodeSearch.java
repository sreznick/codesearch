package org.codesearch;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.codesearch.Units.Unit;
import org.codesearch.golang.GolangIndexer;
import org.codesearch.golang.GolangSearcher;

public class CodeSearch {
    public static abstract class CodeSearcher {
        private Indexer indexer;
        private Searcher searcher;
        Path indexDirPath;

        CodeSearcher(Indexer indexer, Searcher searcher, String indexDirPath) {
            this.indexer = indexer;
            this.searcher = searcher;
            this.indexDirPath = Paths.get(indexDirPath);
        }

        public void buildIndex(String sourceDirPath) throws IOException, InterruptedException {
            indexer.indexSources(Paths.get(sourceDirPath), indexDirPath);
        }

        public List<Unit> codeSearch(List<String> keys, boolean isFuzzy, int numTopDocs) throws IOException {
            return searcher.runQuery(indexDirPath, keys, isFuzzy, numTopDocs);
        }
    }

    public static class GolangCodeSearcher extends CodeSearcher {
        GolangCodeSearcher(String indexDirPath) {
            super(new GolangIndexer(), new GolangSearcher(), indexDirPath);
        }
    }

    // public static class JavaCodeSearcher ...
    // public static class PythonCodeSearcher ...
}
