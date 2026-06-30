package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlyphStitcher.class)
public abstract class GlyphAtlasTextureMixins implements IGlyphAtlasTextureExt {

    @Shadow
    public abstract BakedSheetGlyph stitch(GlyphInfo info, GlyphBitmap glyph);

    @Override
    public BakedSheetGlyph radiance$stitch(GlyphInfo info, IRenderableGlyphExt glyph) {
        return this.stitch(info, glyph);
    }
}
