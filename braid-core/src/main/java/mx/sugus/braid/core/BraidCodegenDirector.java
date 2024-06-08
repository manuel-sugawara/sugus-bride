package mx.sugus.braid.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import mx.sugus.braid.core.plugin.CodegenModule;
import mx.sugus.braid.core.plugin.Identifier;
import mx.sugus.braid.core.plugin.NonShapeCodegenState;
import mx.sugus.braid.core.plugin.ShapeCodegenState;
import mx.sugus.braid.traits.CodegenIgnoreTrait;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;

public final class BraidCodegenDirector {
    private static final Logger LOG = Logger.getLogger(BraidCodegenDirector.class.getName());
    private final FileManifest fileManifest;
    private final BrideCodegenSettings settings;
    private final CodegenModule module;
    private final SymbolProvider symbolProvider;
    private final Model model;

    BraidCodegenDirector(Builder builder) {
        this.model = Objects.requireNonNull(builder.model, "model");
        this.fileManifest = Objects.requireNonNull(builder.fileManifest, "fileManifest");
        this.settings = Objects.requireNonNull(builder.settings, "settings");
        this.symbolProvider = Objects.requireNonNull(builder.symbolProvider, "symbolProvider");
        this.module = Objects.requireNonNull(builder.module, "module");
    }

    public void execute() {
        LOG.fine("Begin model preparation for codegen");
        var sortedShapes = selectedShapes();
        var properties = new HashMap<Identifier, Object>();
        LOG.fine("Running shape reducers");
        for (var reducer : module.shapeReducers()) {
            var init = reducer.init();
            for (var shape : sortedShapes) {
                if (shape.hasTrait(CodegenIgnoreTrait.class)) {
                    continue;
                }
                var javaShapeState = stateForShape(shape);
                init.consume(javaShapeState);
            }
            properties.put(reducer.taskId(), init.finalizeJob());
        }
        LOG.fine("Beginning shape codegen");
        for (var shape : sortedShapes) {
            var javaShapeState = stateForShape(shape, properties);
            module.generateShape(javaShapeState);
        }
        LOG.fine("Beginning non-shape codegen");
        var nonShapeState = stateFor(properties);
        module.generateNonShape(nonShapeState);
    }

    private Collection<Shape> selectedShapes() {
        return module.select(model);
    }

    private ShapeCodegenState stateForShape(Shape shape) {
        return ShapeCodegenState
            .builder()
            .model(model)
            .shape(shape)
            .symbolProvider(symbolProvider)
            .fileManifest(fileManifest)
            .settings(settings)
            .build();
    }

    private ShapeCodegenState stateForShape(Shape shape, Map<Identifier, Object> properties) {
        return ShapeCodegenState
            .builder()
            .model(model)
            .shape(shape)
            .symbolProvider(symbolProvider)
            .fileManifest(fileManifest)
            .settings(settings)
            .properties(properties)
            .build();
    }

    private NonShapeCodegenState stateFor(Map<Identifier, Object> properties) {
        return NonShapeCodegenState
            .builder()
            .model(model)
            .symbolProvider(symbolProvider)
            .fileManifest(fileManifest)
            .settings(settings)
            .properties(properties)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Model model;
        private FileManifest fileManifest;
        private BrideCodegenSettings settings;
        private SymbolProvider symbolProvider;
        private BiFunction<Model, BrideCodegenSettings, SymbolProvider> symbolProviderFactory;
        private CodegenModule module;

        public Builder model(Model model) {
            this.model = model;
            return this;
        }

        public Builder fileManifest(FileManifest fileManifest) {
            this.fileManifest = fileManifest;
            return this;
        }

        public Builder settings(BrideCodegenSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder symbolProvider(SymbolProvider symbolProvider) {
            this.symbolProvider = symbolProvider;
            return this;
        }

        public Builder module(CodegenModule module) {
            this.module = module;
            return this;
        }

        public Builder symbolProviderFactory(BiFunction<Model, BrideCodegenSettings, SymbolProvider> symbolProviderFactory) {
            this.symbolProviderFactory = symbolProviderFactory;
            return this;
        }

        public BraidCodegenDirector build() {
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(fileManifest, "fileManifest");
            Objects.requireNonNull(symbolProviderFactory, "symbolProviderFactory");
            // We prepare here such that afterward the director can be fully
            // immutable.
            prepare();
            return new BraidCodegenDirector(this);
        }

        private void prepare() {
            LOG.fine("Running module configured model early processors");
            var newModel = module.earlyPreprocessModel(model);
            LOG.fine("Running module configured model processors");
            newModel = module.preprocessModel(newModel);
            this.model = newModel;
            LOG.fine("Running symbol provider decorators");
            var sourceSymbolProvider = symbolProviderFactory.apply(model, settings);
            // For small models using the cache does not seem to add any measurable value.
            this.symbolProvider = SymbolProvider.cache(module.decorateSymbolProvider(this.model, sourceSymbolProvider));
        }
    }
}
