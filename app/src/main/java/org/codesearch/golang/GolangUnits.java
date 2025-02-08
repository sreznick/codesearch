package org.codesearch.golang;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.codesearch.GoParser.*;

import org.json.*;

// import org.codesearch.golang.Extractors;

public class GolangUnits {

    public static abstract class GolangUnit {
        protected JSONObject data = new JSONObject();

        <T extends ParserRuleContext> GolangUnit(String file, T ctx) {
            data.put("file", file);
            data.put("line", ctx.getStart().getLine());
        }

        public JSONObject getJson() {return data;}
    }

    public static class PackageUnit extends GolangUnit  {
        PackageUnit(String file, PackageClauseContext ctx) {
            super(file, ctx);
            data.put("package", Extractors.fromPackageClause(ctx).get("package"));
        }
    }

    public static class ImportUnit extends GolangUnit {
        ImportUnit(String file, ImportSpecContext ctx) {
            super(file, ctx);
            data.put("import", Extractors.fromImportSpec(ctx).get("import"));
        }
    }

    public static abstract class DeclarationUnit extends GolangUnit{
        <T extends ParserRuleContext> DeclarationUnit(String file, T ctx) {
            super(file, ctx);
            data.put("declaration", new JSONObject());
        }

        protected JSONObject getDeclaration () {return data.getJSONObject("declaration");}
    }

    public static class FunctionDeclarationUnit extends DeclarationUnit {
        FunctionDeclarationUnit(String file, FunctionDeclContext ctx) {
            super(file, ctx);
            getDeclaration().put("func", Extractors.fromFunctionDecl(ctx).get("function_decl"));
        }
    }

    public static class MethodDeclarationUnit extends DeclarationUnit {
        MethodDeclarationUnit(String file, MethodDeclContext ctx) {
            super(file, ctx);
            getDeclaration().put("method", Extractors.fromMethodDecl(ctx).get("method_decl"));
        }
    }

    public static class ConstDeclarationUnit extends DeclarationUnit {
        ConstDeclarationUnit(String file, ConstSpecContext ctx, String identifier, JSONObject type) {
            super(file, ctx);
            getDeclaration()
                .put("const", (new JSONObject())
                    .put("identifier", identifier));
            if (type != null) {
                getDeclaration().getJSONObject("const")
                    .put("type", type);
            }
        }

        public static List<ConstDeclarationUnit> fromSpec(String file, ConstSpecContext ctx) {
            List<ConstDeclarationUnit> res = new ArrayList<>();
            JSONObject const_spec = Extractors.fromConstSpec(ctx).getJSONObject("const_spec");
            JSONArray consts = const_spec.getJSONArray("identifier_list");
            JSONObject type = const_spec.optJSONObject("type_");
            for (int i = 0; i < consts.length(); ++i) {
                res.add(new ConstDeclarationUnit(file, ctx, consts.getString(i), type));
            }
            return res;
        }
    }

    public static class TypeDeclarationUnit extends DeclarationUnit {
        TypeDeclarationUnit(String file, TypeSpecContext ctx) {
            super(file, ctx);
            getDeclaration()
                .put("type", Extractors.fromTypeSpec(ctx).get("type_spec"));
        }
    }

    public static class VarDeclarationUnit extends DeclarationUnit {
        VarDeclarationUnit(String file, VarSpecContext ctx, String identifier, JSONObject type) {
            super(file, ctx);
            getDeclaration()
                .put("var", (new JSONObject())
                    .put("identifier", identifier));
            if (type != null) {
                getDeclaration().getJSONObject("var")
                    .put("type", type);
            }
        }

        public static List<VarDeclarationUnit> fromSpec(String file, VarSpecContext ctx) {
            List<VarDeclarationUnit> res = new ArrayList<>();
            JSONObject var_spec = Extractors.fromVarSpec(ctx).getJSONObject("var_spec");
            JSONArray vars = var_spec.getJSONArray("identifier_list");
            JSONObject type = var_spec.optJSONObject("type_");
            for (int i = 0; i < vars.length(); ++i) {
                res.add(new VarDeclarationUnit(file, ctx, vars.getString(i), type));
            }
            return res;
        }
    }
}
