package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.font.providers.BitmapProvider$Glyph$1")
public abstract class BitmapFontGlyphMixins {

    @Inject(method = "upload(IILcom/mojang/blaze3d/textures/GpuTexture;)V", at = @At("HEAD"))
    private void radiance$mirrorBitmapUpload(int x, int y, GpuTexture texture, CallbackInfo ci) {
        int targetId = TextureTracker.getOrRegisterGuiTexture(texture);
        if (targetId == 0) {
            return;
        }

        Object glyph = radiance$getField((Object) this, "this$0");
        Object imageData = radiance$getField(glyph, "imageData");
        NativeImage image = (NativeImage) radiance$getField(imageData, "image");
        int sourceX = radiance$getInt(glyph, "offsetX");
        int sourceY = radiance$getInt(glyph, "offsetY");
        int width = radiance$getInt(glyph, "width");
        int height = radiance$getInt(glyph, "height");
        int srcSizeInBytes = image.getWidth() * image.getHeight() * image.format().components();

        TextureProxy.queueUpload(image.getPointer(), srcSizeInBytes, image.getWidth(), targetId,
            sourceX, sourceY, x, y, width, height, 0);
    }

    @Unique
    private static int radiance$getInt(Object target, String fieldName) {
        return (Integer) radiance$getField(target, fieldName);
    }

    @Unique
    private static Object radiance$getField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Cannot access 26.2 bitmap glyph field " + fieldName,
                    ex);
            }
        }
        throw new IllegalStateException("Missing 26.2 bitmap glyph field " + fieldName);
    }
}
