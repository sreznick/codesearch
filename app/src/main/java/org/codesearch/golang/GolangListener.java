package org.codesearch.golang;

import org.antlr.v4.runtime.ParserRuleContext;
import org.codesearch.GolangBaseListener;
import org.codesearch.GolangParser;

import java.util.ArrayList;
import java.util.List;


public class GolangListener extends GolangBaseListener {
    private List<GolangUnit> units = new ArrayList<>();
    private String file;

    @Override
    public void enterStructType(GolangParser.StructTypeContext ctx) {
        units.add(new StructTypeDecl(getLine(ctx), ctx.getText()));
    }

    @Override
    public void enterMethodDecl(GolangParser.MethodDeclContext ctx) {
        units.add(new MethodDecl(getLine(ctx), ctx.getText()));
    }

    public void setFile(String file) {
        this.file = file;
    }

    public abstract class GolangUnit {
        private int line;
        private String info;
    
        public GolangUnit(int line, String info) {
            this.line = line;
            this.info = info;
        }
        
        @Override
        public String toString() {
            return String.format("File: %s, Line: %s, Unit: %s", file, line, info);
        }
    }

    private int getLine(ParserRuleContext ctx) {
        return ctx.getStart().getLine();
    }

    private class StructTypeDecl extends GolangUnit {
        public StructTypeDecl(int line, String name) {
            super(line, String.format("struct \"%s\" declaration", name));
        }
    }

    private class MethodDecl extends GolangUnit {
        public MethodDecl(int line, String name) {
            super(line, String.format("method \"%s\" declaration", name));
        }
    }
}

