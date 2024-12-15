package grammars;

import org.antlr.v4.runtime.ParserRuleContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public interface CodeGrepGrammarFacade {
    ParserRuleContext parse(File file) throws IOException;
    String[] getRuleNames();
    ExtendedParseTreeListener createListener();
    boolean isValidFile(Path path);
}
