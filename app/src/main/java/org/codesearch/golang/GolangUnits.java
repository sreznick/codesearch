package org.codesearch.golang;

import java.util.ArrayList;
import java.util.List;
import org.json.*;

import org.antlr.v4.runtime.tree.TerminalNode;

import org.codesearch.GoParser.*;
import org.codesearch.Units.UnitContent;

public class GolangUnits {
    public static abstract class UnitContentString implements UnitContent {
        protected String lit;

        @Override
        public List<String> getKeys() {
            List<String> keys = new ArrayList<>();
            keys.add(getName());
            keys.add(String.format("%s.%s", getName(), lit));
            return keys;
        }

        @Override
        public List<String> getInfoKeys() {
            List<String> keys = new ArrayList<>();
            keys.add(String.format("%s.{STRING}", getName()));
            return keys;
        }

        @Override
        public JSONObject getJson() {
            return (new JSONObject()).put(getName(), lit);
        }
    }

    public static abstract class UnitContentFields implements UnitContent {
        protected List<UnitContent> fields;

        UnitContentFields() {fields = new ArrayList<>();}

        @Override
        public List<String> getKeys() {
            List<String> keys = new ArrayList<>();
            keys.add(getName());
            for (UnitContent field: fields) {
                for (String fieldKey: field.getKeys()) {
                    keys.add(String.format("%s.%s", getName(), fieldKey));
                }
            }
            return keys;
        }

        @Override
        public List<String> getInfoKeys() {
            List<String> keys = new ArrayList<>();
            for (UnitContent field: fields) {
                for (String fieldKey: field.getInfoKeys()) {
                    keys.add(String.format("%s.%s", getName(), fieldKey));
                }
            }
            return keys;
        }

        @Override
        public JSONObject getJson() {
            JSONObject val = new JSONObject();
            for (UnitContent guc: fields) {
                val.put(guc.getName(), guc.getJson().get(guc.getName()));
            }
            return (new JSONObject()).put(getName(), val);
        }

        public List<UnitContent> getFields() {return fields;}
    }

    public static abstract class UnitContentArray implements UnitContent {
        protected List<UnitContent> elems;

        UnitContentArray() {elems = new ArrayList<>();}

        @Override
        public List<String> getKeys() {
            List<String> keys = new ArrayList<>();
            keys.add(getName());
            for (int i = 0; i < elems.size(); ++i) {
                for (String elemKey: elems.get(i).getKeys()) {
                    keys.add(String.format("%s.[%d].%s", getName(), i, elemKey));
                    keys.add(String.format("%s.[].%s", getName(), elemKey));
                }
            }
            return keys;
        }

        @Override
        public List<String> getInfoKeys() {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < elems.size(); ++i) {
                for (String elemKey: elems.get(i).getInfoKeys()) {
                    keys.add(String.format("%s.[?{INT}].%s", getName(), elemKey));
                }
            }
            return keys;
        }

        @Override
        public JSONObject getJson() {
            JSONArray val = new JSONArray();
            for (UnitContent guc: elems) {
                val.put(guc.getJson());
            }
            return (new JSONObject()).put(getName(), val);
        }
    }

    public static class IdUnit extends UnitContentString {
        IdUnit(TerminalNode ctx) {lit = ctx.getText();}

        @Override
        public String getName() {return "id";}
    }

    public static class IdListUnit extends UnitContentArray{
        IdListUnit(IdentifierListContext ctx) {
            for (TerminalNode tn: ctx.IDENTIFIER()) {
                elems.add(new IdUnit(tn));
            }
        }

        @Override
        public String getName() {return "id_list";}
    }

    public static class PackageNameUnit extends IdUnit {
        PackageNameUnit(TerminalNode tn) {super(tn);}

        @Override
        public String getName() {return "package";}
    }

    public static class QualifiedIdentUnit extends UnitContentFields {
        QualifiedIdentUnit(QualifiedIdentContext ctx) {
            fields.add(new PackageNameUnit(ctx.IDENTIFIER(0)));
            fields.add(new IdUnit(ctx.IDENTIFIER(1)));
        }

        @Override
        public String getName() {return "qualified_ident";}
    }

    public static class TypeNameUnit extends UnitContentFields {
        TypeNameUnit(TypeNameContext ctx) {
            if (ctx.IDENTIFIER() != null) {fields.add(new IdUnit(ctx.IDENTIFIER()));} 
            else {fields.add(new QualifiedIdentUnit(ctx.qualifiedIdent()));}
        }

        @Override
        public String getName() {return "name";}
    }

    public static class ParameterUnit extends UnitContentFields {
        ParameterUnit(ParameterDeclContext ctx) {
            fields.add(new TypeUnit(ctx.type_()));
            if (ctx.identifierList() != null) {
                fields.add(new IdListUnit(ctx.identifierList()));
            }
        }

        @Override
        public String getName() {return "param";}
    }

    public static class ParameterListUnit extends UnitContentArray {
        ParameterListUnit(ParametersContext ctx) {
            for (ParameterDeclContext parmCtx: ctx.parameterDecl()) {
                elems.add(new ParameterUnit(parmCtx));
            }
        }

        @Override
        public String getName() {return "param_list";}
    }

    public static class ArrayTypeUnit extends UnitContentFields {
        ArrayTypeUnit(ArrayTypeContext ctx) {
            fields.add(new TypeUnit(ctx.elementType().type_()));
        }

        @Override
        public String getName() {return "array";}
    }

    public static class StructTypeUnit extends UnitContentFields {
        StructTypeUnit(StructTypeContext ctx) {}

        @Override
        public String getName() {return "struct";}
    }

    public static class PointerTypeUnit extends UnitContentFields {
        PointerTypeUnit(PointerTypeContext ctx) {
            fields.add(new TypeUnit(ctx.type_()));
        }

        @Override
        public String getName() {return "pointer";}
    }
    
    public static class ResultUnit extends UnitContentFields {
        ResultUnit(ResultContext ctx) {
            if (ctx.parameters() != null) {fields.add(new ParameterListUnit(ctx.parameters()));}
            else {fields.add(new TypeUnit(ctx.type_()));}
        }

        @Override
        public String getName() {return "result";}
    }

    public static class SignatureUnit extends UnitContentFields {
        SignatureUnit(SignatureContext ctx) {
            fields.add(new ParameterListUnit(ctx.parameters()));
            if (ctx.result() != null) {
                fields.add(new ResultUnit(ctx.result()));
            }
        }

        @Override
        public String getName() {return "signature";}
    }

    public static class FunctionTypeUnit extends UnitContentFields {
        FunctionTypeUnit(FunctionTypeContext ctx) {
            fields.add(new SignatureUnit(ctx.signature()));
        }

        @Override
        public String getName() {return "function";}
    }

    public static class MethodSpecUnit extends UnitContentFields {
        MethodSpecUnit(MethodSpecContext ctx) {
            fields.add(new IdUnit(ctx.IDENTIFIER()));
            fields.add(new ParameterListUnit(ctx.parameters()));
            if (ctx.result() != null) {
                fields.add(new ResultUnit(ctx.result()));
            }
        }

        @Override
        public String getName() {return "method";}
    }

    public static class InterfaceTypeUnit extends UnitContentArray {
        InterfaceTypeUnit(InterfaceTypeContext ctx) {
            for (MethodSpecContext method: ctx.methodSpec()) {
                elems.add(new MethodSpecUnit(method));
            }
        }

        @Override
        public String getName() {return "interface";}
    }

    public static class SliceTypeUnit extends UnitContentFields {
        SliceTypeUnit(SliceTypeContext ctx) {
            fields.add(new TypeUnit(ctx.elementType().type_()));
        }

        @Override
        public String getName() {return "slice";}
    }

    public static class KeyUnit extends TypeUnit {
        KeyUnit(Type_Context ctx) {super(ctx);}

        @Override
        public String getName() {return "key";}
    }

    public static class ValueUnit extends TypeUnit {
        ValueUnit(Type_Context ctx) {super(ctx);}

        @Override
        public String getName() {return "value";}
    }

    public static class MapTypeUnit extends UnitContentFields {
        MapTypeUnit(MapTypeContext ctx) {
            fields.add(new KeyUnit(ctx.type_()));
            fields.add(new ValueUnit(ctx.elementType().type_()));
        }

        @Override
        public String getName() {return "map";}
    }

    public static class ChannelTypeUnit extends UnitContentFields {
        ChannelTypeUnit(ChannelTypeContext ctx) {
            fields.add(new TypeUnit(ctx.elementType().type_()));
        }

        @Override
        public String getName() {return "channel";}
    }

    public static class TypeLitUnit extends UnitContentFields {
        TypeLitUnit(TypeLitContext ctx) {
            if (ctx.arrayType() != null) {
                fields.add(new ArrayTypeUnit(ctx.arrayType()));
            } else if (ctx.structType() != null) {
                fields.add(new StructTypeUnit(ctx.structType()));
            } else if (ctx.pointerType() != null) {
                fields.add(new PointerTypeUnit(ctx.pointerType()));
            } else if (ctx.functionType() != null) {
                fields.add(new FunctionTypeUnit(ctx.functionType()));
            } else if (ctx.interfaceType() != null) {
                fields.add(new InterfaceTypeUnit(ctx.interfaceType()));
            } else if (ctx.sliceType() != null) {
                fields.add(new SliceTypeUnit(ctx.sliceType()));
            } else if (ctx.mapType() != null) {
                fields.add(new MapTypeUnit(ctx.mapType()));
            } else {
                fields.add(new ChannelTypeUnit(ctx.channelType()));
            }
        }

        @Override
        public String getName() {return "lit";}
    }

    public static class TypeUnit extends UnitContentFields {
        TypeUnit(Type_Context ctx) {
            while (ctx.type_() != null) {ctx = ctx.type_();}
            if (ctx.typeName() != null) {
                fields.add(new TypeNameUnit(ctx.typeName()));
                if (ctx.typeArgs() != null) {
                    fields.add(new TypeArgListUnit(ctx.typeArgs()));
                }
            }
            else {fields.add(new TypeLitUnit(ctx.typeLit()));}
        }

        @Override
        public String getName() {return "type";}
    }

    public static class TypeListUnit extends UnitContentArray {
        TypeListUnit(TypeListContext ctx) {
            for (Type_Context typeCtx: ctx.type_()) {
                elems.add(new TypeUnit(typeCtx));
            }
        }

        @Override
        public String getName() {return "type_list";}
    }

    public static class TypeArgListUnit extends TypeListUnit {
        TypeArgListUnit(TypeArgsContext ctx) {super(ctx.typeList());}

        @Override
        public String getName() {return "type_arg_list";}
    }

    public static class PackageUnit extends UnitContentFields {
        PackageUnit(PackageClauseContext ctx) {
            fields.add(new IdUnit(ctx.IDENTIFIER()));
        }

        @Override
        public String getName() {return "package";}
    }

    public static class StringUnit extends UnitContentString {
        StringUnit(String_Context ctx) {
            lit = ctx.getText();
        }

        @Override
        public String getName() {return "string";}
    }

    public static class PathUnit extends StringUnit {
        PathUnit(String_Context ctx) {super(ctx);}

        @Override
        public String getName() {return "path";}
    }

    public static class AliasUnit extends IdUnit {
        AliasUnit(TerminalNode tn) {super(tn);}

        @Override
        public String getName() {return "alias";}
    }

    public static class ImportUnit extends UnitContentFields {
        ImportUnit(ImportSpecContext ctx) {
            fields.add(new PathUnit(ctx.importPath().string_()));
            if (ctx.IDENTIFIER() != null) {
                fields.add(new AliasUnit(ctx.IDENTIFIER()));
            }
        }

        @Override
        public String getName() {return "import";}
    }

    public static class TypeParameterUnit extends UnitContentFields {
        TypeParameterUnit(TypeParameterDeclContext ctx) {
            fields.add(new TypeUnit(ctx.typeElement().typeTerm(0).type_()));
            fields.add(new IdListUnit(ctx.identifierList()));
        }

        @Override
        public String getName() {return "param";}
    }

    public static class TypeParameterListUnit extends UnitContentArray {
        TypeParameterListUnit(TypeParametersContext ctx) {
            for (TypeParameterDeclContext parmCtx: ctx.typeParameterDecl()) {
                elems.add(new TypeParameterUnit(parmCtx));
            }
        }

        @Override
        public String getName() {return "param_list";}
    }

    public static class FunctionDeclUnit extends UnitContentFields {
        FunctionDeclUnit(FunctionDeclContext ctx) {
            fields.add(new IdUnit(ctx.IDENTIFIER()));
            fields.add(new SignatureUnit(ctx.signature()));
            if (ctx.typeParameters() != null) {
                fields.add(new TypeParameterListUnit(ctx.typeParameters()));
            }
        }

        @Override
        public String getName() {return "function";}
    }

    public static class ReceiverUnit extends ParameterListUnit {
        ReceiverUnit(ReceiverContext ctx) {super(ctx.parameters());}

        @Override
        public String getName() {return "receiver";}
    }

    public static class MethdoDeclUnit extends UnitContentFields {
        MethdoDeclUnit(MethodDeclContext ctx) {
            fields.add(new ReceiverUnit(ctx.receiver()));
            fields.add(new IdUnit(ctx.IDENTIFIER()));
            fields.add(new SignatureUnit(ctx.signature()));
        }

        @Override
        public String getName() {return "method";}
    }

    public static class TypeAliasUnit extends UnitContentFields {
        TypeAliasUnit(AliasDeclContext ctx) {
            fields.add(new IdUnit(ctx.IDENTIFIER()));
            fields.add(new TypeUnit(ctx.type_()));
        }

        @Override
        public String getName() {return "alias";}
    }

    public static class TypeSpecUnit extends UnitContentFields {
        TypeSpecUnit(TypeSpecContext ctx) {
            if (ctx.aliasDecl() != null) {fields.add(new TypeAliasUnit(ctx.aliasDecl()));}
            else {
                fields.add(new IdUnit(ctx.typeDef().IDENTIFIER()));
                fields.add(new TypeUnit(ctx.typeDef().type_()));
                if (ctx.typeDef().typeParameters() != null) {
                    fields.add(new TypeParameterListUnit(ctx.typeDef().typeParameters()));
                }
            }
        }

        @Override
        public String getName() {return "type";}
    }

    public static class VarSpecUnit extends UnitContentFields {
        VarSpecUnit(TerminalNode tn, Type_Context ctx) {
            fields.add(new IdUnit(tn));
            if (ctx != null) {
                fields.add(new TypeUnit(ctx));
            }
        }

        public static List<VarSpecUnit> fromSpec(VarSpecContext ctx) {
            List<VarSpecUnit> units = new ArrayList<>();
            for (TerminalNode tn: ctx.identifierList().IDENTIFIER()) {
                units.add(new VarSpecUnit(tn, ctx.type_()));
            }
            return units;
        }

        @Override
        public String getName() {return "var";}
    }

    public static class ConstSpecUnit extends UnitContentFields {
        ConstSpecUnit(TerminalNode tn, Type_Context ctx) {
            fields.add(new IdUnit(tn));
            if (ctx != null) {
                fields.add(new TypeUnit(ctx));
            }
        }

        public static List<ConstSpecUnit> fromSpec(ConstSpecContext ctx) {
            List<ConstSpecUnit> units = new ArrayList<>();
            for (TerminalNode tn: ctx.identifierList().IDENTIFIER()) {
                units.add(new ConstSpecUnit(tn, ctx.type_()));
            }
            return units;
        }

        @Override
        public String getName() {return "const";}
    }

    public static class DeclarationUnit extends UnitContentFields {
        DeclarationUnit(ConstSpecUnit unit) {fields.add(unit);}
        DeclarationUnit(VarSpecUnit unit) {fields.add(unit);}
        DeclarationUnit(TypeSpecUnit unit) {fields.add(unit);}

        public static List<DeclarationUnit> fromDeclaration(DeclarationContext ctx) {
            List<DeclarationUnit> units = new ArrayList<>();
            if (ctx.constDecl() != null) {
                for (ConstSpecContext constCtx: ctx.constDecl().constSpec()) {
                    for (ConstSpecUnit unit: ConstSpecUnit.fromSpec(constCtx)) {
                        units.add(new DeclarationUnit(unit));
                    }
                }
            } else if (ctx.varDecl() != null) {
                for (VarSpecContext varCtx: ctx.varDecl().varSpec()) {
                    for (VarSpecUnit unit: VarSpecUnit.fromSpec(varCtx)) {
                        units.add(new DeclarationUnit(unit));
                    }
                }
            } else {
                for (TypeSpecContext typeCtx: ctx.typeDecl().typeSpec()) {
                    units.add(new DeclarationUnit(new TypeSpecUnit(typeCtx)));
                }
            }
            return units;
        }

        @Override
        public String getName() {return "declaration";}
    }

    public static class NilUnit extends UnitContentFields {
        @Override
        public String getName() {return "nil";}
    }

    public static class IntegerUnit extends UnitContentString {
        IntegerUnit(IntegerContext ctx) {
            lit = ctx.getText();
        }

        @Override
        public String getName() {return "int";}
    }

    public static class FloatUnit extends UnitContentString {
        FloatUnit(TerminalNode ctx) {
            lit = ctx.getText();
        }

        @Override
        public String getName() {return "float";}
    }

    public static class BasicLitUnit extends UnitContentFields {
        BasicLitUnit(BasicLitContext ctx) {
            if (ctx.NIL_LIT() != null) {fields.add(new NilUnit());}
            else if (ctx.integer() != null) {fields.add(new IntegerUnit(ctx.integer()));}
            else if (ctx.string_() != null) {fields.add(new StringUnit(ctx.string_()));}
            else {fields.add(new FloatUnit(ctx.FLOAT_LIT()));}
        }

        @Override
        public String getName() {return "basic";}
    }

    public static class EllipsisUnit extends UnitContentFields {
        @Override
        public String getName() {return "ellipsis";}
    }

    public static class LiteralTypeUnit extends UnitContentFields {
        LiteralTypeUnit(LiteralTypeContext ctx) {
            if (ctx.structType() != null) {fields.add(new StructTypeUnit(ctx.structType()));}
            else if (ctx.arrayType() != null) {fields.add(new ArrayTypeUnit(ctx.arrayType()));}
            else if (ctx.ELLIPSIS() != null) {
                fields.add(new EllipsisUnit());
                fields.add(new TypeUnit(ctx.elementType().type_()));
            }
            else if (ctx.sliceType() != null) {fields.add(new SliceTypeUnit(ctx.sliceType()));}
            else if (ctx.mapType() != null) {fields.add(new MapTypeUnit(ctx.mapType()));}
            else {fields.add(new TypeNameUnit(ctx.typeName()));}
        }

        @Override
        public String getName() {return "type";}
    }

    public static class FunctionLitUnit extends UnitContentFields {
        FunctionLitUnit(FunctionLitContext ctx) {
            fields.add(new SignatureUnit(ctx.signature()));
        }

        @Override
        public String getName() {return "function";}
    }

    public static class LiteralUnit extends UnitContentFields {
        LiteralUnit(LiteralContext ctx) {
            if (ctx.basicLit() != null) {fields.add(new BasicLitUnit(ctx.basicLit()));}
            else if (ctx.compositeLit() != null) {
                fields.add(new LiteralTypeUnit(ctx.compositeLit().literalType()));
            }
            else {fields.add(new FunctionLitUnit(ctx.functionLit()));}      
        }

        @Override
        public String getName() {return "literal";}
    }

    public static class FieldUnit extends UnitContentFields {
        FieldUnit(TerminalNode idCtx, Type_Context typeCtx) {
            fields.add(new IdUnit(idCtx));
            fields.add(new TypeUnit(typeCtx));
        }

        FieldUnit(TypeNameContext ctx) {
            fields.add(new TypeNameUnit(ctx));
        }

        public static List<FieldUnit> fromFieldDecl(FieldDeclContext ctx) {
            if (ctx.embeddedField() != null) {
                return List.of(new FieldUnit(ctx.embeddedField().typeName()));
            }
            List<FieldUnit> fields = new ArrayList<>();
            for (TerminalNode idCtx: ctx.identifierList().IDENTIFIER()) {
                fields.add(new FieldUnit(idCtx, ctx.type_()));
            }
            return fields;
        }

        @Override
        public String getName() {return "field";}
    }

    public static class PrimaryExprUnit extends UnitContentFields {
        PrimaryExprUnit(PrimaryExprContext ctx) {
            if (ctx.operand() != null) {fields.add(new OperandUnit(ctx.operand()));}
            else if (ctx.conversion() != null) {fields.add(new ConversionUnit(ctx.conversion()));}
            else if (ctx.methodExpr() != null) {fields.add(new MethodExprUnit(ctx.methodExpr()));}
        }

        @Override
        public String getName() {return "expression";}
    }
    
    public static class OperandUnit extends UnitContentFields {
        OperandUnit(OperandContext ctx) {
            if (ctx.literal() != null) {fields.add(new LiteralUnit(ctx.literal()));}
            else if (ctx.operandName() != null) {
                fields.add(new IdUnit(ctx.operandName().IDENTIFIER()));
                if (ctx.typeArgs() != null) {
                    fields.add(new TypeArgListUnit(ctx.typeArgs()));
                }
            }
        }

        @Override
        public String getName() {return "operand";}
    }

    public static class ConversionUnit extends UnitContentFields {
        ConversionUnit(ConversionContext ctx) {
            fields.add(new TypeUnit(ctx.type_()));
        }

        @Override
        public String getName() {return "conversion";}
    }

    public static class MethodExprUnit extends UnitContentFields {
        MethodExprUnit(MethodExprContext ctx) {
            fields.add(new TypeUnit(ctx.type_()));
            fields.add(new IdUnit(ctx.IDENTIFIER()));
        }

        @Override
        public String getName() {return "method";}
    }
}
