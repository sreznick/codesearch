package org.codesearch.plugin;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.function.Function;

public final class AntlrParsing {
    private AntlrParsing() {}

    public static <P extends Parser> ParseTree parse(
            Lexer lexer,
            Function<CommonTokenStream, P> parserFactory,
            Function<P, ParseTree> entryRule
    ) {
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        P parser = parserFactory.apply(tokens);
        parser.removeErrorListeners();

        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        parser.setErrorHandler(new BailErrorStrategy());
        try {
            return entryRule.apply(parser);
        } catch (ParseCancellationException e) {
            tokens.seek(0);
            parser.reset();
            parser.getInterpreter().setPredictionMode(PredictionMode.LL);
            parser.setErrorHandler(new DefaultErrorStrategy());
            return entryRule.apply(parser);
        }
    }
}
