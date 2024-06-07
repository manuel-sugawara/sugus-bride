package mx.sugus.braid.core.symbol;

import java.util.function.Function;
import mx.sugus.braid.core.SymbolConstants.AggregateType;
import mx.sugus.braid.core.util.Name;
import mx.sugus.braid.jsyntax.Block;
import mx.sugus.braid.jsyntax.TypeName;
import mx.sugus.braid.traits.UseBuilderReferenceTrait;
import software.amazon.smithy.codegen.core.Property;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Symbol properties.
 */
public final class SymbolProperties {
    /**
     * Property for the java type for a given symbol.
     */
    public static final Property<TypeName> JAVA_TYPE = from(TypeName.class);

    /**
     * Property for the java name for a given symbol.
     */
    public static final Property<Name> SIMPLE_NAME = from(Name.class);

    /**
     * Property for the type of aggregate the symbol represents.
     */
    public static final Property<AggregateType> AGGREGATE_TYPE = from(AggregateType.class);

    /**
     * Property for when a member should use a builder reference as in the given trait.
     */
    public static final Property<UseBuilderReferenceTrait> BUILDER_REFERENCE = from(UseBuilderReferenceTrait.class);

    /**
     * Property to flag if the shape has to preserve insertion order. Valid for sets and maps.
     */
    public static final Property<Boolean> ORDERED = from(Boolean.class);

    /**
     * The method name in the class to get the value for the symbol.
     */
    public static final Property<String> GETTER = from(String.class);

    /**
     * The method name in the builder to set the value for the symbol.
     */
    public static final Property<String> SETTER = from(String.class);

    /**
     * The method name in the builder to add to a symbol representing a collection.
     */
    public static final Property<String> ADDER = from(String.class);

    /**
     * The method name in the builder to put in a symbol representing a map.
     */
    public static final Property<String> PUTTER = from(String.class);

    /**
     * The empty builder initializer code.
     */
    public static final Property<Function<Symbol, Block>> EMPTY_BUILDER_INIT = Property.named("empty-builder-init");

    /**
     * From data builder initializer code.
     */
    public static final Property<Function<Symbol, Block>> DATA_BUILDER_INIT = Property.named("data-builder-init");

    private SymbolProperties() {
    }

    private static <T> Property<T> from(Class<T> kclass) {
        return Property.named(kclass.getName());
    }
}