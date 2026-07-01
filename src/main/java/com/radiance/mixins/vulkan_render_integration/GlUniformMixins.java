package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {
    "com.mojang.blaze3d.opengl.Uniform$Ubo",
    "com.mojang.blaze3d.opengl.Uniform$Utb",
    "com.mojang.blaze3d.opengl.Uniform$Sampler"
})
public abstract class GlUniformMixins implements IGlUniformExt {

    private static final IntBuffer EMPTY_INT_BUFFER = IntBuffer.allocate(0);
    private static final FloatBuffer EMPTY_FLOAT_BUFFER = FloatBuffer.allocate(0);

    @Override
    public boolean radiance$hasCpuDataValue() {
        return false;
    }

    @Override
    public int radiance$getDataTypeValue() {
        return 0;
    }

    @Override
    public int radiance$getCountValue() {
        return 0;
    }

    @Override
    public IntBuffer radiance$getIntDataValue() {
        return EMPTY_INT_BUFFER.duplicate();
    }

    @Override
    public FloatBuffer radiance$getFloatDataValue() {
        return EMPTY_FLOAT_BUFFER.duplicate();
    }

    @Override
    public int radiance$getBlockBindingValue() {
        return radiance$intAccessor("blockBinding", -1);
    }

    @Override
    public int radiance$getLocationValue() {
        return radiance$intAccessor("location", -1);
    }

    @Override
    public int radiance$getSamplerIndexValue() {
        return radiance$intAccessor("samplerIndex", -1);
    }

    @Override
    public int radiance$getTextureValue() {
        return radiance$intAccessor("texture", 0);
    }

    @Override
    public String radiance$getFormatNameValue() {
        Object format = radiance$objectAccessor("format");
        return format == null ? null : format.toString();
    }

    private int radiance$intAccessor(String methodName, int fallback) {
        Object value = radiance$objectAccessor(methodName);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private Object radiance$objectAccessor(String methodName) {
        try {
            Method method = this.getClass().getMethod(methodName);
            return method.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
