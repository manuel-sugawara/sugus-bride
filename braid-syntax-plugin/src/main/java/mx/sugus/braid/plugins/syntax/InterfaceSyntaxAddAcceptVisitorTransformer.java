package mx.sugus.braid.plugins.syntax;

import mx.sugus.braid.core.plugin.Identifier;
import mx.sugus.braid.core.plugin.ShapeCodegenState;
import mx.sugus.braid.core.plugin.ShapeTaskTransformer;
import mx.sugus.braid.core.plugin.TypeSyntaxResult;
import mx.sugus.braid.jsyntax.AbstractMethodSyntax;
import mx.sugus.braid.jsyntax.ClassName;
import mx.sugus.braid.jsyntax.InterfaceSyntax;
import mx.sugus.braid.jsyntax.ParameterizedTypeName;
import mx.sugus.braid.jsyntax.TypeVariableTypeName;
import mx.sugus.braid.plugins.data.producers.StructureInterfaceJavaProducer;
import mx.sugus.braid.plugins.data.producers.Utils;
import mx.sugus.braid.traits.InterfaceTrait;
import software.amazon.smithy.model.shapes.ShapeId;

public final class InterfaceSyntaxAddAcceptVisitorTransformer implements ShapeTaskTransformer<TypeSyntaxResult> {
    private static final Identifier ID = Identifier.of(SyntaxAddAcceptVisitorTransformer.class);
    private final String syntaxNode;

    public InterfaceSyntaxAddAcceptVisitorTransformer(String syntaxNode) {
        this.syntaxNode = syntaxNode;
    }

    @Override
    public Identifier taskId() {
        return ID;
    }

    @Override
    public Identifier transformsId() {
        return StructureInterfaceJavaProducer.ID;
    }

    @Override
    public TypeSyntaxResult transform(TypeSyntaxResult result, ShapeCodegenState state) {
        var shape = state.shape();
        if (!shape.hasTrait(InterfaceTrait.class)) {
            return result;
        }
        if (shape.getId().toString().equals(syntaxNode)) {
            var syntaxShape = state.model().expectShape(ShapeId.from(syntaxNode));
            var syntaxNodeClass = ClassName.toClassName(Utils.toJavaTypeName(state, syntaxShape));
            var visitorClass = ClassName.from(syntaxNodeClass.packageName(), syntaxNodeClass.name() +
                                                                             "Visitor");
            var visitorResultTypeVar = TypeVariableTypeName.builder()
                                                           .name("VisitorR")
                                                           .build();
            var visitor = ParameterizedTypeName.from(visitorClass, visitorResultTypeVar);
            var syntax = ((InterfaceSyntax) result.syntax()).toBuilder()
                                                            .addMethod(AbstractMethodSyntax.builder()
                                                                                           .name("accept")
                                                                                           .returns(visitorResultTypeVar)
                                                                                           .addTypeParam(visitorResultTypeVar)
                                                                                           .addParameter(visitor, "visitor")
                                                                                           .build())
                                                            .build();
            return result.toBuilder()
                         .syntax(Utils.addGeneratedBy(syntax, SyntaxModelPlugin.ID))
                         .build();
        }
        return result;
    }

    public String syntaxNode() {
        return syntaxNode;
    }

}
