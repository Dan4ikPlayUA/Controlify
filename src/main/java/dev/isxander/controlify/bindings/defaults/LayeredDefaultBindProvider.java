package dev.isxander.controlify.bindings.defaults;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.isxander.controlify.bindings.input.Input;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public record LayeredDefaultBindProvider(List<Layer> layers) implements DefaultBindProvider {
    public static final Codec<LayeredDefaultBindProvider> CODEC = Codec.list(Layer.CODEC)
            .xmap(LayeredDefaultBindProvider::new, LayeredDefaultBindProvider::layers);

    public static DefaultBindProvider of(Layer... layers) {
        return new LayeredDefaultBindProvider(Arrays.asList(layers));
    }

    @Override
    @Nullable
    public Input getDefaultBind(ResourceLocation bindId) {
        for (var layer : layers()) {
            Input input = layer.provider().getDefaultBind(bindId);
            if (input != null) return input;
            if (layer.clearBelow()) return null;
        }
        return null;
    }

    public record Layer(DefaultBindProvider provider, boolean clearBelow) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MapBackedDefaultBindProvider.MAP_CODEC.forGetter(layer -> (MapBackedDefaultBindProvider) layer.provider()),
                Codec.BOOL.fieldOf("clear_below").forGetter(Layer::clearBelow)
        ).apply(instance, Layer::new));
    }
}
