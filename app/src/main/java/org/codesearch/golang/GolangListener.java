package org.codesearch.golang;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import org.codesearch.GoParserBaseListener;
import org.codesearch.GoParser.*;
import org.codesearch.Units.*;
import org.codesearch.golang.GolangUnits.*;


public class GolangListener extends GoParserBaseListener {
    private String file;
    private List<Unit> units = new ArrayList<>();

    public void setFile(String file) {this.file = file;}

    public List<Unit> getUnits() {return units;}
    
    private int getLine(ParserRuleContext ctx) {
        return ctx.getStart().getLine();
    }

    private int getPosition(ParserRuleContext ctx) {
        return ctx.getStart().getCharPositionInLine();
    }

    @Override
    public void enterPackageClause(PackageClauseContext ctx) {
        units.add(new Unit(file, getLine(ctx), getPosition(ctx), new PackageUnit(ctx)));
    }

    @Override
    public void enterImportSpec(ImportSpecContext ctx) {
        units.add(new Unit(file, getLine(ctx), getPosition(ctx), new ImportUnit(ctx)));
    }

    @Override
    public void enterFunctionDecl(FunctionDeclContext ctx) {
        units.add(new Unit(file, getLine(ctx), getPosition(ctx), new FunctionDeclUnit(ctx)));
    }

    @Override
    public void enterMethodDecl(MethodDeclContext ctx) {
        units.add(new Unit(file, getLine(ctx), getPosition(ctx), new MethdoDeclUnit(ctx)));
    }

    @Override
    public void enterDeclaration(DeclarationContext ctx) {
        for (DeclarationUnit unit: DeclarationUnit.fromDeclaration(ctx)) {
            units.add(new Unit(file, getLine(ctx), getPosition(ctx), unit));
        }
    }

    @Override
    public void enterLiteral(LiteralContext ctx) {
        units.add(new Unit(file, getLine(ctx), getPosition(ctx), new LiteralUnit(ctx)));
    }

    @Override
    public void enterFieldDecl(FieldDeclContext ctx) {
        for (FieldUnit unit: FieldUnit.fromFieldDecl(ctx)) {
            units.add(new Unit(file, getLine(ctx), getPosition(ctx), unit));
        }
    }

    @Override public void enterPrimaryExpr(PrimaryExprContext ctx) {
        PrimaryExprUnit unit = new PrimaryExprUnit(ctx);
        if (unit.getFields().size() != 0) {
            units.add(new Unit(file, getLine(ctx), getPosition(ctx), new PrimaryExprUnit(ctx)));
        }
    }
}
