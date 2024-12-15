package grammars.python3.facade;

import codegrep.data.FileReference;
import codegrep.data.ObjectType;
import codegrep.data.ProjectContents;
import grammars.ExtendedParseTreeListener;
import grammars.python3.Python3Parser;
import grammars.python3.Python3ParserBaseListener;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Python3Logic extends Python3ParserBaseListener implements ExtendedParseTreeListener {
    // TODO: this should be moved into Lucene later on, also needs optimizations
    private final List<FileReference> references = new ArrayList<>();
    private Path path;

    @Override
    public void setPath(Path path) {
        this.path = path;
    }

    private String cleanup(TerminalNode node) {
        String s = node.toString();
        // strip s from quotation symbols on its sides
        if (s.startsWith("'''") || s.startsWith("\"\"\""))
            return s.substring(3, s.length() - 3);
        return s.substring(1, s.length() - 1);
    }

    @Override
    public void enterAtom(Python3Parser.AtomContext ctx) {
        super.enterAtom(ctx);
        List<TerminalNode> items = ctx.STRING();
        if (items.isEmpty()) return;
        String literal = items.stream().reduce("",
                (s, n) -> s + cleanup(n), (s, s2) -> s + s2);
        int lineno = ctx.getStart().getLine();
        int size = ctx.getText().split("\n").length - 1;
        references.add(new FileReference(path, ObjectType.STRING_LITERAL, literal, lineno, lineno + size));
    }

    @Override
    public ProjectContents getProjectContents() {
        return new ProjectContents(references);
    }
}
