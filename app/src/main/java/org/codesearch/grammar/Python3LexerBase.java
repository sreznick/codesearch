package org.codesearch.grammar;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;

public abstract class Python3LexerBase extends Lexer {
    private final LinkedList<Token> pending = new LinkedList<>();
    private final Deque<Integer> indents = new ArrayDeque<>();
    private int opened;
    private Token lastToken;

    protected Python3LexerBase(CharStream input) {
        super(input);
    }

    @Override
    public void emit(Token token) {
        super.setToken(token);
        pending.offer(token);
    }

    @Override
    public Token nextToken() {
        if (_input.LA(1) == EOF && !indents.isEmpty()) {
            pending.removeIf(token -> token.getType() == EOF);
            emit(commonToken(Python3Lexer.NEWLINE, "\n"));
            while (!indents.isEmpty()) {
                emit(createDedent());
                indents.pop();
            }
            emit(commonToken(EOF, "<EOF>"));
        }

        Token next = super.nextToken();
        if (next.getChannel() == Token.DEFAULT_CHANNEL) {
            lastToken = next;
        }
        return pending.isEmpty() ? next : pending.poll();
    }

    protected boolean atStartOfInput() {
        return getCharPositionInLine() == 0 && getLine() == 1;
    }

    protected void openBrace() {
        opened++;
    }

    protected void closeBrace() {
        opened = Math.max(0, opened - 1);
    }

    protected void onNewLine() {
        String newLine = getText().replaceAll("[^\r\n\f]+", "");
        String spaces = getText().replaceAll("[\r\n\f]+", "");

        int next = _input.LA(1);
        int nextNext = _input.LA(2);
        if (opened > 0 || (nextNext != -1 && (next == '\r' || next == '\n' || next == '\f' || next == '#'))) {
            skip();
            return;
        }

        emit(commonToken(Python3Lexer.NEWLINE, newLine));
        int indent = indentationCount(spaces);
        int previous = indents.isEmpty() ? 0 : indents.peek();
        if (indent == previous) {
            skip();
        } else if (indent > previous) {
            indents.push(indent);
            emit(commonToken(Python3Lexer.INDENT, spaces));
        } else {
            while (!indents.isEmpty() && indents.peek() > indent) {
                emit(createDedent());
                indents.pop();
            }
        }
    }

    private Token createDedent() {
        CommonToken dedent = commonToken(Python3Lexer.DEDENT, "");
        if (lastToken != null) {
            dedent.setLine(lastToken.getLine());
        }
        return dedent;
    }

    private CommonToken commonToken(int type, String text) {
        int stop = getCharIndex() - 1;
        int start = text.isEmpty() ? stop : stop - text.length() + 1;
        return new CommonToken(_tokenFactorySourcePair, type, DEFAULT_TOKEN_CHANNEL, start, stop);
    }

    private static int indentationCount(String spaces) {
        int count = 0;
        for (char ch : spaces.toCharArray()) {
            count += ch == '\t' ? 8 - (count % 8) : 1;
        }
        return count;
    }
}
