package org.codesearch.grammar;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class JavaParserBase extends Parser {
    protected JavaParserBase(TokenStream input) {
        super(input);
    }

    public boolean IsNotIdentifierAssign() {
        return !(_input.LA(1) == JavaParser.IDENTIFIER && _input.LA(2) == JavaParser.ASSIGN);
    }

    public boolean DoLastRecordComponent() {
        return true;
    }
}
