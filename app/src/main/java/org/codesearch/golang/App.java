package org.codesearch.golang;

import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import org.codesearch.golang.GolangUnits.GolangUnit;
import org.json.JSONArray;
import org.codesearch.GoLexer;
import org.codesearch.GoParser;

public class App {
    public static void main(String[] args) throws Exception {
        String file_name = "src/test/java/org/example/test.go";
        String file_out = "src/test/java/org/example/out.json";
        String content = String.join("\n", Files.readAllLines(Paths.get(file_name)));
        Listener listener = new Listener();
        listener.setFile(file_name);
        GoLexer lexer = new GoLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GoParser parser = new GoParser(tokens);

        ParseTree tree = parser.sourceFile();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);

        JSONArray res = new JSONArray();
        for (GolangUnit unit: listener.getUnits()) {
            res.put(unit.getJson());
        }

        Files.writeString(Paths.get(file_out), res.toString());
    }
}
