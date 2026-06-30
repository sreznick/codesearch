package org.example;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class GoParserBase extends Parser {
    protected GoParserBase(TokenStream input) {
        super(input);
    }

    protected boolean closingBracket() {
        int tokenType = _input.LA(1);
        return tokenType == GoParser.R_CURLY || tokenType == GoParser.R_PAREN;
    }
}
