package org.codesearch.grammar;

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class GoParserBase extends Parser {
    protected GoParserBase(TokenStream input) {
        super(input);
    }

    protected boolean closingBracket() {
        BufferedTokenStream stream = (BufferedTokenStream) _input;
        int tokenType = stream.LA(1);
        return tokenType == GoParser.R_CURLY || tokenType == GoParser.R_PAREN;
    }
}
