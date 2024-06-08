package mx.sugus.braid.plugins.data.producers;

import mx.sugus.braid.core.plugin.Identifier;
import mx.sugus.braid.core.plugin.ShapeCodegenState;
import mx.sugus.braid.core.plugin.ShapeProducerTask;
import mx.sugus.braid.plugins.data.TypeSyntaxResult;
import mx.sugus.braid.traits.InterfaceTrait;
import mx.sugus.braid.traits.JavaTrait;
import software.amazon.smithy.model.shapes.ShapeType;

public final class StructureJavaProducer implements ShapeProducerTask<TypeSyntaxResult> {
    public static final Identifier ID = Identifier.of(StructureJavaProducer.class);

    public StructureJavaProducer() {
    }

    @Override
    public Identifier taskId() {
        return ID;
    }

    @Override
    public Class<TypeSyntaxResult> output() {
        return TypeSyntaxResult.class;
    }

    @Override
    public ShapeType type() {
        return ShapeType.STRUCTURE;
    }

    @Override
    public TypeSyntaxResult produce(ShapeCodegenState directive) {
        var shape = directive.shape();
        if (shape.hasTrait(JavaTrait.class)) {
            return null;
        }
        if (shape.hasTrait(InterfaceTrait.class)) {
            return null;
        }
        var syntax = StructureData.INSTANCE.build(directive);
        return TypeSyntaxResult.builder()
                               .syntax(syntax)
                               .build();
    }
}