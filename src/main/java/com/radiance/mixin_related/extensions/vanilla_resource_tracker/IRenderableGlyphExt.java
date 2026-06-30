package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.textures.GpuTexture;

public interface IRenderableGlyphExt extends GlyphBitmap {

    void upload(int id, int x, int y);

    void upload(int x, int y);

    int getWidth();

    int getHeight();

    boolean hasColor();

    default float getBearingX() {
        return 0.0f;
    }

    default float getAscent() {
        return getHeight();
    }

    @Override
    default int getPixelWidth() {
        return getWidth();
    }

    @Override
    default int getPixelHeight() {
        return getHeight();
    }

    @Override
    default void upload(int x, int y, GpuTexture texture) {
        upload(x, y);
    }

    @Override
    default boolean isColored() {
        return hasColor();
    }

    @Override
    default float getLeft() {
        return getBearingX();
    }

    @Override
    default float getRight() {
        return getBearingX() + getWidth() / getOversample();
    }

    @Override
    default float getTop() {
        return getAscent();
    }

    @Override
    default float getBottom() {
        return getAscent() - getHeight() / getOversample();
    }
}
