package com.radiance.client.option;

import static com.radiance.client.option.Options.UPSCALER_TYPE_FSR3;
import static com.radiance.client.option.Options.UPSCALER_TYPE_NATIVE;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum UpscalerType implements StringRepresentable {
    NATIVE(0, "native", UPSCALER_TYPE_NATIVE),
    FSR3(1, "fsr3", UPSCALER_TYPE_FSR3);

    public static final Codec<UpscalerType> Codec =
        StringRepresentable.fromValues(UpscalerType::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    UpscalerType(final int ordinal, final String name, final String translationKey) {
        this.ordinal = ordinal;
        this.name = name;
        this.translationKey = translationKey;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public int getId() {
        return this.ordinal;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }
}
