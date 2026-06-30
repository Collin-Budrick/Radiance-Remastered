package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IGlyphAtlasTextureExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.IRenderableGlyphExt;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.font.FontSet$2")
public class FontStorageMixins {

    @Redirect(method = "stitch(Lcom/mojang/blaze3d/font/GlyphInfo;Lcom/mojang/blaze3d/font/GlyphBitmap;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/GlyphStitcher;stitch(Lcom/mojang/blaze3d/font/GlyphInfo;Lcom/mojang/blaze3d/font/GlyphBitmap;)Lnet/minecraft/client/gui/font/glyphs/BakedSheetGlyph;"))
    private BakedSheetGlyph radiance$stitchRenderableGlyph(GlyphStitcher stitcher, GlyphInfo info,
        GlyphBitmap glyph) {
        if (glyph instanceof IRenderableGlyphExt renderableGlyph) {
            return ((IGlyphAtlasTextureExt) stitcher).radiance$stitch(info, renderableGlyph);
        }
        return stitcher.stitch(info, glyph);
    }
}
