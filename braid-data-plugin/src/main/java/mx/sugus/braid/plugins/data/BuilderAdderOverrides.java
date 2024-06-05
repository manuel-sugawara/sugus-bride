package mx.sugus.braid.plugins.data;

import static mx.sugus.braid.plugins.data.StructureCodegenUtils.getTargetListMember;
import static mx.sugus.braid.plugins.data.StructureCodegenUtils.getTargetListMemberTrait;
import static mx.sugus.braid.plugins.data.StructureCodegenUtils.getTargetTrait;
import static mx.sugus.braid.plugins.data.StructureCodegenUtils.toParameters;
import static mx.sugus.braid.core.util.Utils.coalesce;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import mx.sugus.braid.core.SymbolConstants;
import mx.sugus.braid.core.plugin.ShapeCodegenState;
import mx.sugus.braid.jsyntax.ClassName;
import mx.sugus.braid.jsyntax.ClassSyntax;
import mx.sugus.braid.jsyntax.CodeBlock;
import mx.sugus.braid.jsyntax.FieldSyntax;
import mx.sugus.braid.jsyntax.MethodSyntax;
import mx.sugus.braid.jsyntax.block.BodyBuilder;
import mx.sugus.braid.jsyntax.ext.JavadocExt;
import mx.sugus.braid.traits.AddAllOverridesTrait;
import mx.sugus.braid.traits.AdderOverridesTrait;
import mx.sugus.braid.traits.BuilderOverride;
import mx.sugus.braid.traits.FromFactoriesTrait;
import mx.sugus.braid.traits.MultiAddOverridesTrait;
import software.amazon.smithy.model.shapes.MemberShape;

public final class BuilderAdderOverrides implements DirectedClass {
    static final BuilderAdderOverrides INSTANCE = new BuilderAdderOverrides();

    private BuilderAdderOverrides() {
    }

    @Override
    public ClassName className(ShapeCodegenState state) {
        return StructureCodegenUtils.BUILDER_TYPE;
    }

    @Override
    public ClassSyntax.Builder typeSpec(ShapeCodegenState state) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FieldSyntax> fieldsFor(ShapeCodegenState state, MemberShape member) {
        return List.of();
    }


    @Override
    public List<MethodSyntax> methodsFor(ShapeCodegenState state, MemberShape member) {
        var aggregateType = state.symbolProvider().aggregateType(member);
        if (aggregateType == SymbolConstants.AggregateType.NONE) {
            return List.of();
        }
        var result = new ArrayList<MethodSyntax>();
        var adderOverrides = getTargetTrait(AdderOverridesTrait.class, state, member);
        if (adderOverrides != null) {
            addAdderOverrides(result, state, member, adderOverrides.getValues());
        }
        var listMemberFromFactories = getTargetListMemberTrait(FromFactoriesTrait.class, state, member);
        if (listMemberFromFactories != null) {
            addImplicitAdderOverrides(result, state, member, listMemberFromFactories.getValues());
        }
        var addAllOverrides = getTargetTrait(AddAllOverridesTrait.class, state, member);
        if (addAllOverrides != null) {
            addAddAllOverrides(result, state, member, addAllOverrides);
        }
        var multiAddOverrides = getTargetTrait(MultiAddOverridesTrait.class, state, member);
        if (multiAddOverrides != null) {
            addMultiAddOverrides(result, state, member, multiAddOverrides);
        }
        return result;
    }

    private void addAdderOverrides(
        List<MethodSyntax> methods,
        ShapeCodegenState state,
        MemberShape member,
        List<BuilderOverride> builderOverrides
    ) {
        var name = state.symbolProvider().toJavaName(member);
        var symbolProvider = state.symbolProvider();
        for (var override : builderOverrides) {
            var adderName = coalesce(override.getName(),
                                     () -> symbolProvider.toJavaSingularName(member, "add").toString());
            var overrideBuilder = MethodSyntax.builder(adderName)
                                              .addModifier(Modifier.PUBLIC)
                                              .returns(className(state));
            overrideBuilder.parameters(toParameters(override.getArgs()));
            overrideBuilder.body(body -> {
                addValue(state, member, body, override.getBody());
                body.addStatement("return this");
            });
            var javadoc = coalesce(override.getJavadoc(), () -> "Adds to `" + name + "` building the value using the given "
                                                                + "arguments");
            overrideBuilder.javadoc("$L", JavadocExt.document(javadoc));
            methods.add(overrideBuilder.build());
        }
    }

    private void addImplicitAdderOverrides(
        List<MethodSyntax> methods,
        ShapeCodegenState state,
        MemberShape member,
        List<BuilderOverride> builderOverrides
    ) {
        var name = state.symbolProvider().toJavaName(member);
        var symbolProvider = state.symbolProvider();
        for (var override : builderOverrides) {
            var adderName = coalesce(override.getName(),
                                     () -> symbolProvider.toJavaSingularName(member, "add").toString());
            var overrideBuilder = MethodSyntax.builder(adderName)
                                              .addModifier(Modifier.PUBLIC)
                                              .returns(className(state));
            overrideBuilder.parameters(toParameters(override.getArgs()));
            overrideBuilder.body(body -> {
                addValueFromImplicitOverride(state, member, body, override);
                body.addStatement("return this");
            });
            var javadoc = coalesce(override.getJavadoc(), () -> "Adds to `" + name + "` building the value using the given "
                                                                + "arguments");
            overrideBuilder.javadoc("$L", JavadocExt.document(javadoc));
            methods.add(overrideBuilder.build());
        }
    }

    private void addAddAllOverrides(
        List<MethodSyntax> methods,
        ShapeCodegenState state,
        MemberShape member,
        AddAllOverridesTrait addAllOverrides
    ) {
        var name = state.symbolProvider().toJavaName(member);
        var symbolProvider = state.symbolProvider();
        for (var override : addAllOverrides.getValues()) {
            var adderName = coalesce(override.getName(),
                                     () -> symbolProvider.toJavaSingularName(member, "add").toString());
            var overrideBuilder = MethodSyntax.builder(adderName)
                                              .addModifier(Modifier.PUBLIC)
                                              .returns(className(state));
            overrideBuilder.parameters(toParameters(override.getArgs()));
            overrideBuilder.body(body -> {
                addAllValue(state, member, body, override.getBody());
                body.addStatement("return this");
            });
            var javadoc = coalesce(override.getJavadoc(), () -> "Adds to `" + name + "` building the values using the given "
                                                                + "arguments");
            overrideBuilder.javadoc("$L", JavadocExt.document(javadoc));
            methods.add(overrideBuilder.build());
        }
    }

    private void addMultiAddOverrides(
        List<MethodSyntax> methods,
        ShapeCodegenState state,
        MemberShape member,
        MultiAddOverridesTrait multiAddOverrides
    ) {
        if (multiAddOverrides == null) {
            return;
        }
        var name = state.symbolProvider().toJavaName(member);
        var symbolProvider = state.symbolProvider();
        for (var override : multiAddOverrides.getValues()) {
            var adderName = coalesce(override.getName(),
                                     () -> symbolProvider.toJavaName(member, "add").toString());
            var overrideBuilder = MethodSyntax.builder(adderName)
                                              .addModifier(Modifier.PUBLIC)
                                              .returns(className(state));
            overrideBuilder.parameters(toParameters(override.getArgs()));
            overrideBuilder.body(body -> {
                addValue(state, member, body, override.getBody());
                body.addStatement("return this");
            });
            var javadoc = coalesce(override.getJavadoc(), () -> "Adds the given values to `" + name + "`");
            overrideBuilder.javadoc("$L", JavadocExt.document(javadoc));
            methods.add(overrideBuilder.build());
        }
    }

    private void addValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder, List<String> values) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        for (var value : values) {
            builder.addStatement("this.$L.asTransient().add($L)", name.toString(), value);
        }
    }

    private void addValueFromImplicitOverride(ShapeCodegenState state, MemberShape member, BodyBuilder builder,
                                              BuilderOverride override) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        var block = CodeBlock.builder();
        var listMemberShape = getTargetListMember(state, member);
        block.addCode("$T.", symbolProvider.toJavaTypeName(listMemberShape));
        if (override.getName() == null) {
            block.addCode("from");
        } else {
            block.addCode(override.getName());
        }
        block.addCode("(");
        var isFirst = true;
        for (var arg : override.getArgs()) {
            if (isFirst) {
                block.addCode("$L", arg.getName());
            } else {
                block.addCode(", $L", arg.getName());
            }
            isFirst = false;
        }
        block.addCode(")");
        builder.addStatement("this.$L.asTransient().add($C)", name.toString(), block.build());
    }

    private void addAllValue(ShapeCodegenState state, MemberShape member, BodyBuilder builder, List<String> values) {
        var symbolProvider = state.symbolProvider();
        var name = symbolProvider.toMemberJavaName(member);
        for (var value : values) {
            builder.addStatement("this.$L.asTransient().addAll($L)", name.toString(), value);
        }
    }
}