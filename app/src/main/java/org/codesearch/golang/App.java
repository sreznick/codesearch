package org.codesearch.golang;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.codesearch.GoLexer;
import org.codesearch.GoParser;
import org.codesearch.Searcher;
import org.codesearch.Units.Unit;
import org.json.JSONArray;
import org.json.JSONObject;

public class App {
    public static void main(String[] args) throws Exception {
        parseTest();
        indexTest();
        searchTest();
    }

    public static void parseTest() throws Exception {
        String fileName = "app/src/test/java/org/codesearch/golang/test.go";
        String fileOut = "app/src/test/java/org/codesearch/golang/out.json";
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
        for (Unit unit: listener.getUnits()) {
            res.put((new JSONObject())
                .put("json", unit.getJSON())
                .put("keys", new JSONArray(unit.getKeys()))
                .put("line", unit.getLine())
                .put("file", unit.getFile())
            );
        }

        Files.writeString(Paths.get(fileOut), res.toString());
    }

    public static void indexTest() throws Exception {
        GolangIndexer indexer = new GolangIndexer();
        String dirPath = "app/src/test/java/org/codesearch/golang";
        String indexPath = "app/index/golang";
        indexer.indexSources(Paths.get(dirPath), Paths.get(indexPath));
    }

    public static void searchTest() throws Exception {
        String indexPath = "app/index/golang";
        List<String> keys = List.of(
            "expression.operand.literal.basic.string"
            // "function.signature.result.type.name.id.int",
            // "function.signature.param_list.[0].param.type.name.id.int"
        );
        List<Unit> res = Searcher.runQuery(Paths.get(indexPath), keys, false, 100);
        System.out.println(String.format("%d docs", res.size()));
        for (Unit unit: res) {
            System.out.println(String.format("unit: [%s:%d]", unit.getFile(), unit.getLine()));
        }
        // out:
        // 7 docs
        // unit: [app/src/test/java/org/codesearch/golang/test.go:18]
        // unit: [app/src/test/java/org/codesearch/golang/test.go:25]
        // unit: [app/src/test/java/org/codesearch/golang/test.go:54]
        // unit: [app/src/test/java/org/codesearch/golang/test.go:71]
        // unit: [app/src/test/java/org/codesearch/golang/test.go:274]
        // unit: [app/src/test/java/org/codesearch/golang/test.go:278]
        // unit: [app/src/test/java/org/codesearch/golang/test.go:297]
    }
}
