package org.codesearch.golang;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import org.codesearch.GoParserBaseListener;
import org.codesearch.GoParser.*;
import org.codesearch.golang.GolangUnits.*;


public class Listener extends GoParserBaseListener {
    private String file;
    private List<GolangUnit> units = new ArrayList<>();

    public void setFile(String file) {this.file = file;}

    public List<GolangUnit> getUnits() {return units;}
    
    private int getLine(ParserRuleContext ctx) {
        return ctx.getStart().getLine();
    }

    @Override
    public void enterPackageClause(PackageClauseContext ctx) {
        units.add(new GolangUnit(file, getLine(ctx), new PackageUnit(ctx)));
    }

    @Override
    public void enterImportSpec(ImportSpecContext ctx) {
        units.add(new GolangUnit(file, getLine(ctx), new ImportUnit(ctx)));
    }

    @Override
    public void enterFunctionDecl(FunctionDeclContext ctx) {
        units.add(new GolangUnit(file, getLine(ctx), new FunctionDeclUnit(ctx)));
    }

    @Override
    public void enterMethodDecl(MethodDeclContext ctx) {
        units.add(new GolangUnit(file, getLine(ctx), new MethdoDeclUnit(ctx)));
    }

    @Override
    public void enterDeclaration(DeclarationContext ctx) {
        for (DeclarationUnit unit: DeclarationUnit.fromDeclaration(ctx)) {
            units.add(new GolangUnit(file, getLine(ctx), unit));
        }
    }
}
