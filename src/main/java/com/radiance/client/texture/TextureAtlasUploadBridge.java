package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.RadianceClient;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ISpriteContentsImagesExt;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

public final class TextureAtlasUploadBridge {

    private static final Field SPRITE_PADDING_FIELD = findSpritePaddingField();

    private TextureAtlasUploadBridge() {
    }

    public static void captureAtlas(Identifier atlasLocation, GpuTexture atlasTexture,
        List<TextureAtlasSprite> sprites) {
        if (atlasLocation == null || atlasTexture == null || sprites == null || sprites.isEmpty()) {
            return;
        }

        TextureTracker.rememberTextureIdentifier(atlasLocation, atlasTexture);

        int captured = 0;
        for (TextureAtlasSprite sprite : sprites) {
            SpriteContents contents = sprite.contents();
            NativeImage[] images =
                ((ISpriteContentsImagesExt) contents).radiance$getMipImages();
            if (images == null) {
                continue;
            }

            Identifier spriteId = contents.name();
            TextureTracker.rememberTextureIdentifier(spriteId, atlasTexture);
            for (int level = 0; level < images.length; level++) {
                NativeImage image = images[level];
                if (image == null) {
                    continue;
                }

                int padding = spritePadding(sprite);
                int dstX = Math.max(0, (sprite.getX() + padding) >> level);
                int dstY = Math.max(0, (sprite.getY() + padding) >> level);
                TextureUploadReplay.capture(atlasTexture, spriteId, image, level, dstX, dstY);
                captured++;
            }
        }

        if (captured > 0) {
            RadianceClient.LOGGER.info(
                "Radiance atlas upload bridge: cached {} sprite mip writes for {}",
                captured, atlasLocation);
        }
    }

    private static Field findSpritePaddingField() {
        try {
            Field field = TextureAtlasSprite.class.getDeclaredField("padding");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            RadianceClient.LOGGER.warn(
                "Radiance atlas upload bridge: TextureAtlasSprite padding field is unavailable; atlas uploads may be shifted",
                ex);
            return null;
        }
    }

    private static int spritePadding(TextureAtlasSprite sprite) {
        if (SPRITE_PADDING_FIELD == null) {
            return 0;
        }
        try {
            return Math.max(0, SPRITE_PADDING_FIELD.getInt(sprite));
        } catch (IllegalAccessException ex) {
            RadianceClient.LOGGER.warn(
                "Radiance atlas upload bridge: failed to read TextureAtlasSprite padding", ex);
            return 0;
        }
    }
}
