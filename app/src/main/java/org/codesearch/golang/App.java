package org.codesearch.golang;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONObject;

import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import org.codesearch.golang.GolangUnits.GolangUnit;
import org.codesearch.GoLexer;
import org.codesearch.GoParser;

public class App {
    public static void main(String[] args) throws Exception {
        indexTest();
    }

    public static void parseTest() throws Exception {
        String fileName = "src/test/java/org/codesearch/golang/test.go";
        String fileOut = "src/test/java/org/codesearch/golang/out.json";
        String content = String.join("\n", Files.readAllLines(Paths.get(fileName)));
        GolangListener listener = new GolangListener();
        listener.setFile(fileName);
        GoLexer lexer = new GoLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GoParser parser = new GoParser(tokens);

        ParseTree tree = parser.sourceFile();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);

        JSONArray res = new JSONArray();
        for (GolangUnit unit: listener.getUnits()) {
            res.put((new JSONObject())
                .put("json", unit.getJSON())
                .put("keys", new JSONArray(unit.getKeys()))
            );
        }

        Files.writeString(Paths.get(fileOut), res.toString());
    }

    public static void indexTest() throws Exception {
        GolangIndexer indexer = new GolangIndexer();
        String dirPath = "src/test/java/org/codesearch/golang";
        String fileOut = "index/golang";
        indexer.indexSources(Paths.get(dirPath), Paths.get(fileOut));
    }
}
