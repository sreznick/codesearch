package grammars.python3.facade;

import grammars.python3.Python3Parser;
import grammars.python3.Python3ParserBaseListener;

import java.util.ArrayList;
import java.util.List;

public class Python3Logic extends Python3ParserBaseListener {
    private final List<Python3Parser.AtomContext> atomBuilders = new ArrayList<>();

    @Override
    public void enterTrailer(Python3Parser.TrailerContext ctx) {
        super.enterTrailer(ctx);
        System.out.println("enter trailer: " + ctx.getText());
    }

    @Override
    public void enterAtom(Python3Parser.AtomContext ctx) {
        super.enterAtom(ctx);
        System.out.println("enter atom: " + ctx.getText());
        atomBuilders.add(ctx);
    }

    @Override
    public void exitAtom(Python3Parser.AtomContext ctx) {
        super.exitAtom(ctx);
        System.out.println("exit atom: " + ctx.getText());
        if (atomBuilders.get(atomBuilders.size() - 1) == ctx) {
            atomBuilders.remove(atomBuilders.size() - 1);
        } else {
            throw new RuntimeException("stack rule did not work");
        }
    }
}
