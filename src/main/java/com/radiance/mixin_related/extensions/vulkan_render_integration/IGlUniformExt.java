package com.radiance.mixin_related.extensions.vulkan_render_integration;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public interface IGlUniformExt {

    default boolean radiance$hasCpuDataValue() {
        return true;
    }

    int radiance$getDataTypeValue();

    int radiance$getCountValue();

    IntBuffer radiance$getIntDataValue();

    FloatBuffer radiance$getFloatDataValue();

    default int radiance$getBlockBindingValue() {
        return -1;
    }

    default int radiance$getLocationValue() {
        return -1;
    }

    default int radiance$getSamplerIndexValue() {
        return -1;
    }

    default int radiance$getTextureValue() {
        return 0;
    }

    default String radiance$getFormatNameValue() {
        return null;
    }
}
