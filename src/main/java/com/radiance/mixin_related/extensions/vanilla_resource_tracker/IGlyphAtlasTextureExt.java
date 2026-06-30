package com.radiance.mixin_related.extensions.vanilla_resource_tracker;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;

public interface IGlyphAtlasTextureExt {

    BakedSheetGlyph radiance$stitch(GlyphInfo info, IRenderableGlyphExt glyph);
}
