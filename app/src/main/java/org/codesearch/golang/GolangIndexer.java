package org.codesearch.golang;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
import org.codesearch.Units.Unit;

public class GolangIndexer extends Indexer {

    @Override
    protected List<String> getExtensions() {
        return List.of(".go");
    }

    @Override
    protected List<Document> indexFile(Path file) throws Exception {
        GolangListener listener = new GolangListener();
        listener.setFile(file.toAbsolutePath().toString());
        String content = String.join("\n", Files.readAllLines(file));
        GoLexer lexer = new GoLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GoParser parser = new GoParser(tokens);

        ParseTree tree = parser.sourceFile();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(listener, tree);

        List<Document> docs = new ArrayList<>();

        for (Unit unit: listener.getUnits()) {
            Document doc = new Document();
            doc.add(new StringField("file", unit.getFile(), Field.Store.YES));;
            doc.add(new StringField("line", String.valueOf(unit.getLine()), Field.Store.YES));
            doc.add(new StringField("position", String.valueOf(unit.getPosition()), Field.Store.YES));
            doc.add(new StringField("json", unit.getJSON().toString(), Field.Store.YES));
            for (String key: unit.getKeys()) {
                doc.add(new StringField("key", key, Field.Store.YES));
            }
            docs.add(doc);
            infoKeys.addAll(unit.getInfoKeys());
        }


        return docs;
    }
}
