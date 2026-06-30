package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.font.GlyphBitmap;

public interface IRenderableGlyphExt extends GlyphBitmap {

    int radiance$getWidth();

    int radiance$getHeight();

    boolean radiance$hasColor();

    default float radiance$getBearingX() {
        return 0.0f;
    }

    default float radiance$getAscent() {
        return radiance$getHeight();
    }

    @Override
    default int getPixelWidth() {
        return radiance$getWidth();
    }

    @Override
    default int getPixelHeight() {
        return radiance$getHeight();
    }

    @Override
    default boolean isColored() {
        return radiance$hasColor();
    }

    @Override
    default float getBearingLeft() {
        return radiance$getBearingX();
    }

    @Override
    default float getBearingTop() {
        return radiance$getAscent();
    }

    @Override
    default float getRight() {
        return radiance$getBearingX() + radiance$getWidth() / getOversample();
    }

    @Override
    default float getBottom() {
        return radiance$getAscent() - radiance$getHeight() / getOversample();
    }
}
