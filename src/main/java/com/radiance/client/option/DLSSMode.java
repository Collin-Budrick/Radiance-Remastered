package com.radiance.client.option;

import static com.radiance.client.option.Options.DLSS_MODE_BALANCED;
import static com.radiance.client.option.Options.DLSS_MODE_DLAA;
import static com.radiance.client.option.Options.DLSS_MODE_PERFORMANCE;
import static com.radiance.client.option.Options.DLSS_MODE_QUALITY;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DLSSMode implements StringRepresentable {
    PERFORMANCE(0, "performance", DLSS_MODE_PERFORMANCE),
    BALANCED(1, "balanced", DLSS_MODE_BALANCED),
    QUALITY(2, "quality", DLSS_MODE_QUALITY),
    DLAA(3, "dlaa", DLSS_MODE_DLAA);

    public static final Codec<DLSSMode> Codec = StringRepresentable.fromValues(DLSSMode::values);
    private final int ordinal;
    private final String name;
    private final String translationKey;

    DLSSMode(final int ordinal, final String name, final String translationKey) {
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
