package mx.sugus.braid.plugins.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.lang.model.element.Modifier;
import mx.sugus.braid.core.SymbolConstants;
import mx.sugus.braid.core.plugin.ShapeCodegenState;
import mx.sugus.braid.core.util.Name;
import mx.sugus.braid.jsyntax.CaseClause;
import mx.sugus.braid.jsyntax.ClassName;
import mx.sugus.braid.jsyntax.ClassSyntax;
import mx.sugus.braid.jsyntax.CodeBlock;
import mx.sugus.braid.jsyntax.ConstructorMethodSyntax;
import mx.sugus.braid.jsyntax.DefaultCaseClause;
import mx.sugus.braid.jsyntax.FieldSyntax;
import mx.sugus.braid.jsyntax.MethodSyntax;
import mx.sugus.braid.jsyntax.ParameterizedTypeName;
import mx.sugus.braid.jsyntax.SwitchStatement;
import mx.sugus.braid.jsyntax.block.BodyBuilder;
import mx.sugus.braid.jsyntax.ext.JavadocExt;
import mx.sugus.braid.rt.util.CollectionBuilderReference;
import mx.sugus.braid.traits.UseBuilderReferenceTrait;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

// TODO: split using CompositeDirectedClass
public class UnionDataBuilder implements DirectedClass {

    @Override
    public ClassName className(ShapeCodegenState state) {
        return StructureCodegenUtils.BUILDER_TYPE;
    }

    @Override
    public ClassSyntax.Builder typeSpec(ShapeCodegenState state) {
        return ClassSyntax.builder("Builder")
                          .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
    }

    @Override
    public List<FieldSyntax> fieldsFor(ShapeCodegenState state, MemberShape member) {
        return List.of();
    }

    @Override
    public List<FieldSyntax> extraFields(ShapeCodegenState state) {
        return List.of(FieldSyntax.builder()
                                  .name("value")
                                  .type(Object.class)
                                  .addModifier(Modifier.PRIVATE)
                                  .build(),
                       FieldSyntax.builder()
                                  .name("type")
                                  .type(ClassName.from("Type"))
                                  .addModifier(Modifier.PRIVATE)
                                  .build());
    }

    @Override
    public List<ConstructorMethodSyntax> constructors(ShapeCodegenState state) {
        return List.of(constructor(), constructorFromData(state));
    }

    @Override
    public List<MethodSyntax> extraMethods(ShapeCodegenState state) {
        return List.of(buildMethod(state));
    }

    private MethodSyntax buildMethod(ShapeCodegenState state) {
        var symbolProvider = state.symbolProvider();
        var shapeType = symbolProvider.toJavaTypeName(state.shape());
        return MethodSyntax.builder("build")
                           .addModifier(Modifier.PUBLIC)
                           .returns(shapeType)
                           .body(b -> b.addStatement("return new $T(this)", shapeType))
                           .build();
    }

    @Override
    public List<MethodSyntax> methodsFor(ShapeCodegenState state, MemberShape member) {
        var aggregateType = state.symbolProvider().aggregateType(member);
        if (aggregateType == SymbolConstants.AggregateType.NONE) {
            if (usesBuilderReference(state, member)) {
                return List.of(setter(state, member), builderReferenceMutator(state, member));
            }
            return List.of(setter(state, member));
        }
        var result = new ArrayList<MethodSyntax>();
        result.addAll(accessor(state, member));
        result.add(setter(state, member));
        result.addAll(adder(state, member));
        return Collections.unmodifiableList(result);
    }

    private List<MethodSyntax> accessor(ShapeCodegenState state, MemberShape member) {
        var symbolProvider = state.symbolProvider();
        var aggregateType = symbolProvider.aggregateType(member);
        if (usesBuilderReference(state, member) || aggregateType != SymbolConstants.AggregateType.NONE) {
            var typeValue = Name.of(member.getMemberName(), Name.Convention.SCREAM_CASE).toString();
            var name = symbolProvider.toMemberName(member);
            var type = StructureDataBuilder.finalTypeForAggregate(state, member);
            var accessor = MethodSyntax.builder(name)
                                       .addAnnotation(UnionData.SUPPRESS_UNCHECKED)
                                       .addModifier(Modifier.PRIVATE)
                                       .returns(type)
                                       .body(b ->
                                                 b.ifStatement("this.type != Type.$L", typeValue, then -> {
                                                     then.addStatement("this.type = Type.$L", typeValue);
                                                     var empty = symbolProvider.emptyReferenceBuilder(aggregateType);
                                                     then.addStatement("$T $L = $T.$L()",
                                                                       type,
                                                                       name,
                                                                       CollectionBuilderReference.class,
                                                                       empty);
                                                     then.addStatement("this.value = $L", name);
                                                     then.addStatement("return $L", name);
                                                 }, otherwise -> {
                                                     otherwise.addStatement("return ($T) this.value", type);
                                                 })
                                       )
                                       .build();
            return List.of(accessor);
        }
        return List.of();
    }

    private MethodSyntax setter(ShapeCodegenState state, MemberShape member) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberName(member);
        var builder = MethodSyntax.builder(name)
                                  .addModifier(Modifier.PUBLIC)
                                  .addParameter(symbolProvider.toJavaTypeName(member), name)
                                  .returns(className(state));
        builder.body(b -> {
            var aggregateType = symbolProvider.aggregateType(member);
            switch (aggregateType) {
                case LIST, SET -> setListValue(state, member, b);
                case MAP -> setMapValue(state, member, b);
                default -> setMemberValue(state, member, b);
            }
            b.addStatement("return this");
        });

        var doc = "Sets the value for `" + name + "`";
        if (member.hasTrait(DocumentationTrait.class)) {
            doc += "\n\n" + member.expectTrait(DocumentationTrait.class).getValue();
        }
        builder.javadoc(CodeBlock.from("$L", JavadocExt.document(doc)));
        return builder.build();
    }

    private void setListValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        var type = StructureDataBuilder.finalTypeForAggregate(state, member);
        builder.addStatement("$T tmp = $L()", type, name);
        builder.addStatement("tmp.clear()");
        builder.addStatement("tmp.asTransient().addAll($L)", name);
    }

    private void setMapValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        var type = StructureDataBuilder.finalTypeForAggregate(state, member);
        builder.addStatement("$T tmp = $L()", type, name);
        builder.addStatement("tmp.clear()");
        builder.addStatement("tmp.asTransient().putAll($L)", name);
    }

    private List<MethodSyntax> adder(ShapeCodegenState state, MemberShape member) {
        var symbolProvider = state.symbolProvider();
        var aggregateType = symbolProvider.aggregateType(member);
        return switch (aggregateType) {
            case LIST, SET -> collectionAdder(state, member);
            case MAP -> mapAdder(state, member);
            default -> throw new IllegalArgumentException("cannot create adder for " + member);
        };
    }

    private List<MethodSyntax> collectionAdder(ShapeCodegenState state, MemberShape member) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toJavaName(member);
        var methodName = symbolProvider.toJavaSingularName(member, "add").toString();
        var builder = MethodSyntax.builder(methodName)
                                  .addModifier(Modifier.PUBLIC)
                                  .returns(className(state));
        addValueParam(state, member, builder);
        var aggregateType = symbolProvider.aggregateType(member);
        builder.body(body -> {
            switch (aggregateType) {
                case LIST, SET -> {
                    var valueArgument = name.toSingularSpelling().toString();
                    addValue(state, member, body, List.of(valueArgument));
                }
                default -> throw new IllegalArgumentException("cannot create adder for " + member);
            }
            body.addStatement("return this");
        });
        var doc = "Adds a single value for `" + name + "`";
        builder.javadoc(CodeBlock.from("$L", JavadocExt.document(doc)));
        var defaultAdder = builder.build();
        var result = new ArrayList<MethodSyntax>();
        result.add(defaultAdder);
        return Collections.unmodifiableList(result);
    }

    private List<MethodSyntax> mapAdder(ShapeCodegenState state, MemberShape member) {
        var symbolProvider = state.symbolProvider();
        var methodName = symbolProvider.toJavaSingularName(member, "put").toString();
        var builder = MethodSyntax.builder(methodName)
                                  .addModifier(Modifier.PUBLIC)
                                  .returns(className(state));
        addKeyValueParam(state, member, builder);
        builder.body(body -> {
            addKeyValue(state, member, body);
            body.addStatement("return this");
        });

        return List.of(builder.build());
    }

    private void addValueParam(ShapeCodegenState state, MemberShape member, MethodSyntax.Builder builder) {
        var symbolProvider = state.symbolProvider();
        var symbol = symbolProvider.toSymbol(member).getReferences().get(0).getSymbol();
        var paramName = symbolProvider.toJavaSingularName(member).toString();
        var paramType = symbolProvider.toJavaTypeName(symbol);
        builder.addParameter(paramType, paramName);
    }

    private void addKeyValueParam(ShapeCodegenState state, MemberShape member, MethodSyntax.Builder builder) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        var paramName = name.toSingularSpelling().toString();
        var symbol = symbolProvider.toSymbol(member);
        var references = symbol.getReferences();
        var keyParamType = symbolProvider.toJavaTypeName(references.get(0).getSymbol());
        var valueParamType = symbolProvider.toJavaTypeName(references.get(1).getSymbol());
        builder.addParameter(keyParamType, "key");
        builder.addParameter(valueParamType, paramName);
    }

    private void addValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder, List<String> values) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        for (var value : values) {
            builder.addStatement("$L().asTransient().add($L)", name.toString(), value);
        }
    }

    private MethodSyntax builderReferenceMutator(ShapeCodegenState state, MemberShape member) {
        var symbolProvider = state.symbolProvider();
        var target = state.model().expectShape(member.getTarget());
        var useReferenceTrait = target.getTrait(UseBuilderReferenceTrait.class).orElseThrow();
        var builderTypeId = useReferenceTrait.builderType();
        var builderType = ClassName.from(builderTypeId.getNamespace(), builderTypeId.getName());
        var name = symbolProvider.toMemberName(member);
        var builder = MethodSyntax.builder(name)
                                  .addModifier(Modifier.PUBLIC)
                                  .addParameter(
                                      ParameterizedTypeName.from(Consumer.class, builderType),
                                      "mutator")
                                  .returns(className(state));
        builder.body(b -> {
            b.addStatement("mutator.accept(this.$L.asTransient())", name);
            b.addStatement("return this");
        });
        return builder.build();
    }

    private void setMemberValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberName(member);
        if (usesBuilderReference(state, member)) {
            setBuilderReferenceValue(state, member, builder);
        } else {
            var typeValue = symbolProvider.toJavaName(member, Name.Convention.SCREAM_CASE);
            builder.addStatement("this.type = Type.$1L", typeValue);
            builder.addStatement("this.value = $L", name);
        }
    }

    private void setBuilderReferenceValue(
        ShapeCodegenState state,
        MemberShape member,
        BodyBuilder bodyBuilder
    ) {
        var name = state.symbolProvider().toMemberName(member);
        bodyBuilder.addStatement("this.$1L.setPersistent($1L)", name);
    }

    private void addKeyValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        var paramName = name.toSingularSpelling().toString();
        builder.addStatement("$L().asTransient().put(key, $L)", name.toString(), paramName);
    }

    ConstructorMethodSyntax constructor() {
        return ConstructorMethodSyntax.builder()
                                      .addStatement("this.type = null")
                                      .addStatement("this.value = Type.UNKNOWN_TO_VERSION")
                                      .build();
    }

    ConstructorMethodSyntax constructorFromData(ShapeCodegenState state) {
        var symbolProvider = state.symbolProvider();
        var shapeSymbol = symbolProvider.toSymbol(state.shape());
        var paramType = ClassName.from(shapeSymbol.getNamespace(), shapeSymbol.getName());
        var builder = ConstructorMethodSyntax.builder()
                                             .addParameter(paramType, "data");
        var withReferenceMember = membersUsingBuilderReference(state);
        if (withReferenceMember.isEmpty()) {
            builder.addStatement("this.type = data.type")
                   .addStatement("this.value = data.value");
        } else {
            builder.body(b -> initBuilderReferences(state, withReferenceMember, b));
        }
        return builder.build();
    }

    private void initBuilderReferences(
        ShapeCodegenState state,
        List<MemberShape> withReferenceMember,
        BodyBuilder builder
    ) {
        builder.addStatement("this.type = data.type");
        var symbolProvider = state.symbolProvider();
        var typeSwitch = SwitchStatement.builder().expression(CodeBlock.from("data.type"));
        for (var member : withReferenceMember) {
            var enumName = symbolProvider.toJavaName(member, Name.Convention.SCREAM_CASE);
            typeSwitch.addCase(CaseClause.builder()
                                         .addLabel(CodeBlock.from("$L", enumName))
                                         .body(b -> {
                                             setValueFromPersistent(state, member, b);
                                             b.addStatement("break");
                                         })
                                         .build());
        }
        typeSwitch.defaultCase(DefaultCaseClause.builder()
                                                .addStatement("this.value = data.value")
                                                .build());
        builder.addStatement(typeSwitch.build());
    }

    private List<MemberShape> membersUsingBuilderReference(ShapeCodegenState state) {
        var symbolProvider = state.symbolProvider();
        var shape = state.shape().asUnionShape().orElseThrow();
        List<MemberShape> result = new ArrayList<>();
        for (var member : shape.getAllMembers().values()) {
            var aggregateType = symbolProvider.aggregateType(member);
            if (aggregateType != SymbolConstants.AggregateType.NONE) {
                result.add(member);
            }
        }
        return result;
    }

    static boolean usesBuilderReference(ShapeCodegenState state, MemberShape member) {
        var target = state.model().expectShape(member.getTarget());
        return target.hasTrait(UseBuilderReferenceTrait.class);
    }

    static void setValueFromPersistent(ShapeCodegenState state, MemberShape member, BodyBuilder builder) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberName(member);
        var aggregateType = symbolProvider.aggregateType(member);
        var init = symbolProvider.initReferenceBuilder(aggregateType);
        builder.addStatement("this.value = $T.$L(data.$L())", CollectionBuilderReference.class,
                             init, name);
    }
}