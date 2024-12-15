package grammars.python3.facade;

import grammars.CodeGrepGrammarFacade;
import grammars.python3.Python3Lexer;
import grammars.python3.Python3Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

public class Python3Facade implements CodeGrepGrammarFacade {
    @Override
    public Python3Parser.File_inputContext parse(File file) throws IOException {
        Python3Lexer lexer = new Python3Lexer(CharStreams.fromPath(Paths.get(file.getPath())));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Python3Parser parser = new Python3Parser(tokens);
        return parser.file_input();
    }

    @Override
    public String[] getRuleNames() {
        return Python3Parser.ruleNames;
    }

    @Override
    public ParseTreeListener createListener() {
        return new Python3Logic();
    }
}