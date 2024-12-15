package grammars;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeListener;

import java.io.File;
import java.io.IOException;

public interface CodeGrepGrammarFacade {
    ParserRuleContext parse(File file) throws IOException;
    String[] getRuleNames();
    ParseTreeListener createListener();
}
