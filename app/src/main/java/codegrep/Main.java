package codegrep;

import grammars.CodeGrepGrammarFacade;
import grammars.python3.facade.Python3Facade;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Main <file>");
            return;
        }

        File f = new File(args[0]);
        CodeGrepGrammarFacade grammar = new Python3Facade();
        ParseTreeWalker walker = new ParseTreeWalker();
        try {
            walker.walk(grammar.createListener(), grammar.parse(f));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // ASTPrinter p = new ASTPrinter(grammar, true);
        // p.process(f);
    }
}
