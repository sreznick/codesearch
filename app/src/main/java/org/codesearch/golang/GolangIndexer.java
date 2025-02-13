package org.codesearch.golang;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field;

import org.codesearch.GoLexer;
import org.codesearch.GoParser;
import org.codesearch.Indexer;
import org.codesearch.golang.GolangUnits.GolangUnit;

public class GolangIndexer extends Indexer {

    @Override
    protected List<String> getExtensions() {
        return List.of(".go");
    }

    @Override
    protected List<Document> indexFile(Path file) throws Exception {
        GolangListener listener = new GolangListener();
        listener.setFile(file.toString());
        String content = String.join("\n", Files.readAllLines(file));
        GoLexer lexer = new GoLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GoParser parser = new GoParser(tokens);

        ParseTree tree = parser.sourceFile();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);

        List<Document> docs = new ArrayList<>();

        for (GolangUnit unit: listener.getUnits()) {
            JSONObject json = unit.getJSON();
            Document doc = new Document();
            for (String key: unit.getKeys()) {
                doc.add(new StringField(key, json.toString(), Field.Store.YES));
            }
            docs.add(doc);
        }
        return docs;
    }
}
