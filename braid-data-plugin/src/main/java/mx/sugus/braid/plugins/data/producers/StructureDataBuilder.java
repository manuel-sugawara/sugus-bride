package mx.sugus.braid.plugins.data.producers;

import static mx.sugus.braid.plugins.data.producers.CodegenUtils.BUILDER_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.lang.model.element.Modifier;
import mx.sugus.braid.core.ImplementsKnowledgeIndex;
import mx.sugus.braid.core.plugin.ShapeCodegenState;
import mx.sugus.braid.jsyntax.ClassName;
import mx.sugus.braid.jsyntax.ClassSyntax;
import mx.sugus.braid.jsyntax.ConstructorMethodSyntax;
import mx.sugus.braid.jsyntax.FieldSyntax;
import mx.sugus.braid.jsyntax.MethodSyntax;
import mx.sugus.braid.jsyntax.ParameterizedTypeName;
import mx.sugus.braid.jsyntax.TypeName;
import mx.sugus.braid.jsyntax.block.BodyBuilder;
import mx.sugus.braid.jsyntax.ext.JavadocExt;
import mx.sugus.braid.rt.util.CollectionBuilderReference;
import mx.sugus.braid.traits.ConstTrait;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

public final class StructureDataBuilder implements DirectedClass {
    static final StructureDataBuilder INSTANCE = new StructureDataBuilder();

    private StructureDataBuilder() {
    }

    @Override
    public ClassName className(ShapeCodegenState state) {
        return BUILDER_TYPE;
    }

    @Override
    public ClassSyntax.Builder typeSpec(ShapeCodegenState state) {
        var builder = ClassSyntax.builder("Builder")
                                 .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
        var shape = state.shape().asStructureShape().orElseThrow();
        var superInterfaces = ImplementsKnowledgeIndex.of(state.model()).superInterfaces(shape);
        for (var superInterface : superInterfaces) {
            var superInterfaceClass = ClassName.toClassName(Utils.toJavaTypeName(state, superInterface));
            builder.addSuperInterface(superInterfaceClass
                                          .toBuilder()
                                          .name(superInterfaceClass.name() + "." + BUILDER_TYPE.name())
                                          .build());
        }

        return builder;
    }

    @Override
    public List<FieldSyntax> fieldsFor(ShapeCodegenState state, MemberShape member) {
        if (member.hasTrait(ConstTrait.class)) {
            return List.of();
        }
        return List.of(fieldFor(state, member));
    }

    @Override
    public List<ConstructorMethodSyntax> constructors(ShapeCodegenState state) {
        return List.of(constructor(state), constructorFromData(state));
    }

    @Override
    public List<MethodSyntax> methodsFor(ShapeCodegenState state, MemberShape member) {
        if (member.hasTrait(ConstTrait.class)) {
            return List.of();
        }
        var result = new ArrayList<MethodSyntax>();
        result.addAll(mutators(state, member));
        result.addAll(setters(state, member));
        result.addAll(adder(state, member));
        return result;
    }

    @Override
    public List<MethodSyntax> extraMethods(ShapeCodegenState state) {
        return List.of(buildMethod(state));
    }

    private FieldSyntax fieldFor(ShapeCodegenState state, MemberShape member) {
        var type = Utils.toBuilderTypeName(state, member);
        var name = Utils.toJavaName(state, member);
        return FieldSyntax.mutableFrom(type, name.toString());
    }

    private ConstructorMethodSyntax constructor(ShapeCodegenState state) {
        var builder = ConstructorMethodSyntax.builder();
        for (var member : state.shape().members()) {
            for (var stmt : Utils.builderInitFromEmpty(state, member).statements()) {
                builder.addStatement(stmt);
            }
        }
        return builder.build();
    }

    private ConstructorMethodSyntax constructorFromData(ShapeCodegenState state) {
        var paramType = ClassName.toClassName(Utils.toJavaTypeName(state, state.shape()));
        var builder = ConstructorMethodSyntax.builder()
                                             .addParameter(paramType, "data");
        for (var member : state.shape().members()) {
            for (var stmt : Utils.builderInitFromData(state, member).statements()) {
                builder.addStatement(stmt);
            }
        }
        return builder.build();
    }

    private MethodSyntax buildMethod(ShapeCodegenState state) {
        var shapeType = Utils.toJavaTypeName(state, state.shape());
        return MethodSyntax.builder("build")
                           .addModifier(Modifier.PUBLIC)
                           .returns(shapeType)
                           .body(b -> b.addStatement("return new $T(this)", shapeType))
                           .build();
    }

    private List<MethodSyntax> setters(ShapeCodegenState state, MemberShape member) {
        return List.of(setter(state, member));
    }

    private MethodSyntax setter(ShapeCodegenState state, MemberShape member) {
        var name = Utils.toJavaName(state, member);
        var builder = methodBuilder(Utils.toSetterName(state, member).toString())
            .addParameter(Utils.toJavaTypeName(state, member), name.toString());

        for (var stmt : Utils.builderSetter(state, member).statements()) {
            builder.addStatement(stmt);
        }
        builder.addStatement("return this");
        var doc = "Sets the value for `" + name + "`";
        if (member.hasTrait(DocumentationTrait.class)) {
            doc += "\n\n" + member.expectTrait(DocumentationTrait.class).getValue();
        }
        builder.javadoc(JavadocExt.document(doc));
        return builder.build();
    }

    private List<MethodSyntax> adder(ShapeCodegenState state, MemberShape member) {
        var aggregateType = Utils.aggregateType(state, member);
        return switch (aggregateType) {
            case LIST, SET -> collectionAdder(state, member);
            case MAP -> mapAdder(state, member);
            default -> List.of();
        };
    }

    private List<MethodSyntax> collectionAdder(ShapeCodegenState state, MemberShape member) {
        var methodName = Utils.toAdderName(state, member);
        var builder = methodBuilder(methodName.toString());
        addValueParam(state, member, builder);
        var name = member.getMemberName();
        builder.body(body -> {
            var valueArgument = Utils.toJavaSingularName(state, member).toString();
            addValue(state, member, body, List.of(valueArgument));
            body.addStatement("return this");
        });
        var doc = "Adds a single value for `" + name + "`";
        builder.javadoc(JavadocExt.document(doc));
        var defaultAdder = builder.build();
        var result = new ArrayList<MethodSyntax>();
        result.add(defaultAdder);
        return result;
    }

    private List<MethodSyntax> mapAdder(ShapeCodegenState state, MemberShape member) {
        var methodName = Utils.toAdderName(state, member).toString();
        var builder = methodBuilder(methodName);
        addKeyValueParam(state, member, builder);
        builder.body(body -> {
            addKeyValue(state, member, body);
            body.addStatement("return this");
        });

        return List.of(builder.build());
    }

    private List<MethodSyntax> mutators(ShapeCodegenState state, MemberShape member) {
        if (Utils.builderReference(state, member) == null) {
            return List.of();
        }
        return List.of(mutator(state, member));
    }

    private MethodSyntax mutator(ShapeCodegenState state, MemberShape member) {
        var builderType = Utils.toRefrenceBuilderBuilderTypeName(state, member);
        var name = Utils.toJavaName(state, member);
        return methodBuilder(name.toString())
            .addParameter(
                ParameterizedTypeName.from(Consumer.class, builderType),
                "mutator")
            .addStatement("mutator.accept(this.$L.asTransient())", name)
            .addStatement("return this")
            .build();
    }

    private void addValueParam(ShapeCodegenState state, MemberShape member, MethodSyntax.Builder builder) {
        var paramName = Utils.toJavaSingularName(state, member).toString();
        var references = state.symbolProvider().toSymbol(member).getReferences();
        builder.addParameter(Utils.toJavaTypeName(state, references.get(0).getSymbol()), paramName);
    }

    private void addKeyValueParam(ShapeCodegenState state, MemberShape member, MethodSyntax.Builder builder) {
        var symbolProvider = state.symbolProvider();
        var symbol = symbolProvider.toSymbol(member);
        var paramName = Utils.toJavaSingularName(state, member).toString();
        var references = symbol.getReferences();
        builder.addParameter(Utils.toJavaTypeName(state, references.get(0).getSymbol()), "key");
        builder.addParameter(Utils.toJavaTypeName(state, references.get(1).getSymbol()), paramName);
    }

    private void addValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder, List<String> values) {
        var name = Utils.toJavaName(state, member);
        for (var value : values) {
            builder.addStatement("this.$L.asTransient().add($L)", name.toString(), value);
        }
    }

    private void addKeyValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder) {
        var name = Utils.toJavaName(state, member);
        var paramName = Utils.toJavaSingularName(state, member).toString();
        builder.addStatement("this.$L.asTransient().put(key, $L)", name.toString(), paramName);
    }

    private MethodSyntax.Builder methodBuilder(String name) {
        return MethodSyntax.builder(name)
                           .addModifier(Modifier.PUBLIC)
                           .returns(BUILDER_TYPE);
    }

    static TypeName finalTypeForAggregate(ShapeCodegenState state, MemberShape member) {
        var innerType = Utils.toJavaTypeName(state, member);
        return ParameterizedTypeName.from(CollectionBuilderReference.class, innerType);
    }


}
