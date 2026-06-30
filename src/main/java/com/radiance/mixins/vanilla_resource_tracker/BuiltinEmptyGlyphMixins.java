package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import java.util.function.Function;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SpecialGlyphs.class)
public abstract class BuiltinEmptyGlyphMixins {

    @Final
    @Shadow
    NativeImage image;

    /**
     * @author LJIONG
     * @reason to pass image targetID
     */
    @Overwrite
    public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
        return function.apply(new IRenderableGlyphExt() {

            @Override
            public int getWidth() {
                return image.getWidth();
            }

            @Override
            public int getHeight() {
                return image.getHeight();
            }

            @Override
            public float getOversample() {
                return 1.0f;
            }

            @Override
            public void upload(int x, int y) {
                image.upload(0, x, y, false);
            }

            @Override
            public void upload(int id, int x, int y) {
                ((INativeImageExt) (Object) image).radiance$setTargetID(id);
                upload(x, y);
            }

            @Override
            public boolean hasColor() {
                return true;
            }
        });
    }
}
