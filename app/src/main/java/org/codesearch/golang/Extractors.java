package org.codesearch.golang;

import org.json.*;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.codesearch.GoParser.*;


public class Extractors {

    public static JSONObject fromIdentifier(TerminalNode ctx) {
        return 
        (new JSONObject())
            .put("IDENTIFIER", ctx.getText());
    }

    public static JSONObject fromIdentifierList(IdentifierListContext ctx) {
        JSONArray val = new JSONArray();
        for (TerminalNode id: ctx.IDENTIFIER()) {
            val.put(fromIdentifier(id).get("IDENTIFIER"));
        }
        return
            (new JSONObject())
                .put("identifier_list", val);
    }

    public static JSONObject fromQualifiedIdent(QualifiedIdentContext ctx) {
        return 
        (new JSONObject())
            .put("qualified_ident", (new JSONObject())
                .put("package_name", fromIdentifier(ctx.IDENTIFIER(0)).get("IDENTIFIER"))
                .put("identifier", fromIdentifier(ctx.IDENTIFIER(1)).get("IDENTIFIER"))
            );
    }

    public static JSONObject fromTypeName(TypeNameContext ctx) {
        Object val = null;
        if (ctx.IDENTIFIER() != null) {val = fromIdentifier(ctx.IDENTIFIER()).get("IDENTIFIER");}
        else {val = fromQualifiedIdent(ctx.qualifiedIdent()).get("qualified_ident");}
        return
            (new JSONObject())
                .put("type_name", val);
    }

    public static JSONObject fromParameters(ParametersContext ctx) {
        JSONArray val = new JSONArray();
        for (ParameterDeclContext param: ctx.parameterDecl()) {
            JSONObject cur = (new JSONObject())
                .put("type_", fromType_(param.type_()).get("type_"));
            if (param.identifierList() != null) {
                cur.put("identifier_list", fromIdentifierList(param.identifierList()).get("identifier_list"));
            }
            val.put(cur);
        }
        return
            (new JSONObject())
                .put("parameters", val);
    }

    public static JSONObject fromArrayType(ArrayTypeContext ctx) {
        return 
            (new JSONObject())
                .put("array_type", fromType_(ctx.elementType().type_()).get("type_"));
    }

    public static JSONObject fromStructType(StructTypeContext ctx) {
        return 
            (new JSONObject())
                .put("struct_type", JSONObject.NULL); // TODO
    }

    public static JSONObject fromPointerType(PointerTypeContext ctx) {
        return 
            (new JSONObject())
                .put("pointer_type", fromType_(ctx.type_()).get("type_"));
    }

    public static JSONObject fromResult(ResultContext ctx) {
        Object val = null;
        if (ctx.parameters() != null) {
            val = fromParameters(ctx.parameters()).get("parameters");
        } else {val = fromType_(ctx.type_()).get("type_");}
        return
            (new JSONObject())
                .put("result", val);
    }

    public static JSONObject fromSignature(SignatureContext ctx) {        
        JSONObject res = (new JSONObject())
            .put("signature", (new JSONObject())
                .put("parameters", fromParameters(ctx.parameters()).get("parameters"))
            );
        if (ctx.result() != null) {
            res.getJSONObject("signature")
                .put("result", fromResult(ctx.result()).get("result"));
        }
        return res;
    }

    public static JSONObject fromFunctionType(FunctionTypeContext ctx) {
        return
            (new JSONObject())
                .put("function_type", fromSignature(ctx.signature()).get("signature"));
    }

    public static JSONObject fromMethodSpec(MethodSpecContext ctx) {
        JSONObject res = new JSONObject()
            .put("method_spec", (new JSONObject())
                .put("identifier", fromIdentifier(ctx.IDENTIFIER()).get("IDENTIFIER"))
                .put("parameters", fromParameters(ctx.parameters()).get("parameters"))
            );
        if (ctx.result() != null) {
            res.getJSONObject("method_spec")
                .put("result", fromResult(ctx.result()).get("result"));
        }
        return res;
    }

    public static JSONObject fromInterfaceType(InterfaceTypeContext ctx) {
        JSONArray methods = new JSONArray();
        for (MethodSpecContext method: ctx.methodSpec()) {
            methods.put(fromMethodSpec(method).get("method"));
        }
        return
            (new JSONObject())
                .put("interface_type", methods);
    }

    public static JSONObject fromSliceType(SliceTypeContext ctx) {
        return
            (new JSONObject())
                .put("slice_type", fromType_(ctx.elementType().type_()).get("type_"));
    }

    public static JSONObject fromMapType(MapTypeContext ctx) {
        return
            (new JSONObject())
                .put("map_type", (new JSONObject())
                    .put("key", fromType_(ctx.type_()).get("type_"))
                    .put("value", fromType_(ctx.elementType().type_()).get("type_"))
                );
    }

    public static JSONObject fromChannelType(ChannelTypeContext ctx) {
        return 
            (new JSONObject())
                .put("channel_type", fromType_(ctx.elementType().type_()).get("type_"));
    }

    public static JSONObject fromType_(Type_Context ctx) {
        Object val = null;
        if (ctx.typeName() != null) {val = fromTypeName(ctx.typeName()).get("type_name");}
        else if (ctx.typeLit() != null) {
            TypeLitContext lit = ctx.typeLit();
            if (lit.arrayType() != null) {val = fromArrayType(lit.arrayType()).get("array_type");} 
            else if (lit.structType() != null) {val = fromStructType(lit.structType()).get("struct_type");}
            else if (lit.pointerType() != null) {val = fromPointerType(lit.pointerType()).get("pointer_type");} 
            else if (lit.functionType() != null) {val = fromFunctionType(lit.functionType()).get("function_type");} 
            else if (lit.interfaceType() != null) {val = fromInterfaceType(lit.interfaceType()).get("interface_type");}
            else if (lit.sliceType() != null) {val = fromSliceType(lit.sliceType()).get("slice_type");}
            else if (lit.mapType() != null) {val = fromMapType(lit.mapType()).get("map_type");}
            else {val = fromChannelType(lit.channelType()).get("channel_type");}
        }
        else if (ctx.type_() != null) {val = fromType_(ctx).get("type_");}
        return 
            (new JSONObject())
                .put("type_", val);
    }

    public static JSONObject fromPackageClause(PackageClauseContext ctx) {
        return
            (new JSONObject())
                .put("package", fromIdentifier(ctx.IDENTIFIER()).get("IDENTIFIER"));
    }

    public static JSONObject fromString_(String_Context ctx) {
        return
            (new JSONObject())
                .put("string_", ctx.getText());
    }

    public static JSONObject fromImportSpec(ImportSpecContext ctx) {
        JSONObject res = (new JSONObject())
           .put("import", (new JSONObject())
                .put("path", fromString_(ctx.importPath().string_()).get("string_"))
           );
        if (ctx.IDENTIFIER() != null) {
            res.getJSONObject("import")
                .put("alias", fromIdentifier(ctx.IDENTIFIER()).get("IDENTIFIER"));
        }
        return res;
    }

    public static JSONObject fromTypeElement(TypeElementContext ctx) {
        JSONArray val = new JSONArray();
        for (TypeTermContext term: ctx.typeTerm()) {
            val.put(fromType_(term.type_()).get("type_"));
        }
        return
            (new JSONObject())
                .put("type_element", val);
    }

    public static JSONObject fromTypeParameterDecl(TypeParameterDeclContext ctx) {
        return
            (new JSONObject())
                .put("type_parameter_decl", (new JSONObject())
                    .put("identifier_list", fromIdentifierList(ctx.identifierList()).get("identifier_list"))
                    .put("type_element", fromTypeElement(ctx.typeElement()).get("type_element"))
                );
    }

    public static JSONObject fromTypeParameters(TypeParametersContext ctx) {
        JSONArray val = new JSONArray();
        for (TypeParameterDeclContext param: ctx.typeParameterDecl()) {
            val.put(fromTypeParameterDecl(param).get("type_parameter_decl"));
        }
        return
            (new JSONObject())
                .put("type_parameters", val);
    }

    public static JSONObject fromFunctionDecl(FunctionDeclContext ctx) {
        JSONObject res = (new JSONObject())
            .put("function_decl", (new JSONObject())
                .put("identifier", fromIdentifier(ctx.IDENTIFIER()).get("IDENTIFIER"))
                .put("signature", fromSignature(ctx.signature()).get("signature"))
            );
        if (ctx.typeParameters() != null) {
            res.getJSONObject("function_delc")
                .put("type_parameters", fromTypeParameters(ctx.typeParameters()).get("type_parameters"));
        }
        return res;
    }

    public static JSONObject fromMethodDecl(MethodDeclContext ctx) {
        return
            (new JSONObject())
                .put("method_decl", (new JSONObject())
                    .put("receiver", fromParameters(ctx.receiver().parameters()).get("parameters"))
                    .put("identifier", fromIdentifier(ctx.IDENTIFIER()).get("IDENTIFIER"))
                    .put("signature", fromSignature(ctx.signature()).get("signature"))
                );
    }

    public static JSONObject fromTypeSpec(TypeSpecContext ctx) {
        if (ctx.aliasDecl() != null) {
            return 
                (new JSONObject())
                    .put("type_spec", (new JSONObject())
                        .put("identifier", fromIdentifier(ctx.aliasDecl().IDENTIFIER()).get("IDENTIFIER"))
                        .put("type_", fromType_(ctx.aliasDecl().type_()).get("type_"))
            );
        }
        JSONObject res = (new JSONObject())
            .put("type_spec", (new JSONObject())
                .put("identifier", fromIdentifier(ctx.typeDef().IDENTIFIER()).get("IDENTIFIER"))
                .put("type_", fromType_(ctx.typeDef().type_()).get("type_"))
            );
        if (ctx.typeDef().typeParameters() != null) {
            res.getJSONObject("type_spec")
                .put("type_parameters", fromTypeParameters(ctx.typeDef().typeParameters()).get("type_parameters"));
        }
        return res;
    }

    public static JSONObject fromVarSpec(VarSpecContext ctx) {
        JSONObject res = (new JSONObject())
            .put("var_spec", fromIdentifierList(ctx.identifierList()));
        if (ctx.type_() != null) {
            res.getJSONObject("var_spec")
                .put("type_", fromType_(ctx.type_()).get("type_"));
        }
        return res;
    }

    public static JSONObject fromConstSpec(ConstSpecContext ctx) {
        JSONObject res = (new JSONObject())
            .put("const_spec", fromIdentifierList(ctx.identifierList()));
        if (ctx.type_() != null) {
            res.getJSONObject("const_spec")
                .put("type_", fromType_(ctx.type_()).get("type_"));
        }
        return res;
    }
}
