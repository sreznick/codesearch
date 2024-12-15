package codegrep;

import grammars.CodeGrepGrammarFacade;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ASTPrinter {
    private final CodeGrepGrammarFacade facade;
    private final String[] ruleNames;
    private final boolean verbose;

    public ASTPrinter(CodeGrepGrammarFacade f, boolean v) {
        facade = f;
        ruleNames = facade.getRuleNames();
        verbose = v;
    }

    public ASTPrinter(CodeGrepGrammarFacade f) {
        this(f, false);
    }

    public void process(File file) {
        try {
            explore(facade.parse(file), 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void explore(RuleContext ctx, int indentation) {
        List<RuleContext> innerRules = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree element = ctx.getChild(i);
            if (element instanceof RuleContext) {
                innerRules.add((RuleContext) element);
            }
        }

        if (innerRules.size() == 1 && !verbose) {
            explore(innerRules.get(0), indentation);
            return;
        }

        System.out.print("  ".repeat(indentation));
        String text = ruleNames[ctx.getRuleIndex()];
        if (innerRules.isEmpty())
            text += " -> " + ctx.getText();
        System.out.println(text);
        for (RuleContext rule : innerRules) {
            explore(rule, indentation + 1);
        }
    }
}
