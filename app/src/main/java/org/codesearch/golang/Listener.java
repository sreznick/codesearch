package org.codesearch.golang;

import java.util.ArrayList;
import java.util.List;

import org.codesearch.GoParserBaseListener;
import org.codesearch.GoParser.*;


public class Listener extends GoParserBaseListener {
    private String file;
    private List<GolangUnits.GolangUnit> units = new ArrayList<>();

    public void setFile(String file) {
        this.file = file;
    }

    public List<GolangUnits.GolangUnit> getUnits() {return units;}
    
    @Override
    public void enterPackageClause(PackageClauseContext ctx) {
        units.add(new GolangUnits.PackageUnit(file, ctx));
    }

    @Override
    public void enterImportSpec(ImportSpecContext ctx) {
        units.add(new GolangUnits.ImportUnit(file, ctx));
    }

    @Override
    public void enterFunctionDecl(FunctionDeclContext ctx) {
        units.add(new GolangUnits.FunctionDeclarationUnit(file, ctx));
    }

    @Override
    public void enterMethodDecl(MethodDeclContext ctx) {
        units.add(new GolangUnits.MethodDeclarationUnit(file, ctx));
    }

    @Override
    public void enterConstSpec(ConstSpecContext ctx) {
        units.addAll(GolangUnits.ConstDeclarationUnit.fromSpec(file, ctx));
    }

    @Override
    public void enterTypeSpec(TypeSpecContext ctx) {
        units.add(new GolangUnits.TypeDeclarationUnit(file, ctx));
    }

    @Override
    public void enterVarSpec(VarSpecContext ctx) {
        units.addAll(GolangUnits.VarDeclarationUnit.fromSpec(file, ctx));
    }
}
