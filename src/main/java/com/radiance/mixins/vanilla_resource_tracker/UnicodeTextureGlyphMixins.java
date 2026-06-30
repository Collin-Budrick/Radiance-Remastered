package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.TextureTracker;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.font.providers.UnihexProvider$Glyph$2")
public abstract class UnicodeTextureGlyphMixins {

    @Inject(method = "upload(IILcom/mojang/blaze3d/textures/GpuTexture;)V", at = @At("HEAD"))
    private void radiance$mirrorUnicodeUpload(int x, int y, GpuTexture texture, CallbackInfo ci) {
        int targetId = TextureTracker.getOrRegisterGuiTexture(texture);
        if (targetId == 0) {
            return;
        }

        Object glyph = radiance$getField((Object) this, "this$0");
        UnihexProvider.LineData contents =
            (UnihexProvider.LineData) radiance$getField(glyph, "contents");
        int left = radiance$getInt(glyph, "left");
        int right = radiance$getInt(glyph, "right");
        int width = right - left + 1;

        IntBuffer pixels = MemoryUtil.memAllocInt(width * 16);
        try {
            radiance$unpackBitsToBytes(pixels, contents, left, right);
            pixels.rewind();
            TextureProxy.queueUpload(MemoryUtil.memAddress(pixels), width * 16 * 4, width,
                targetId, 0, 0, x, y, width, 16, 0);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    @Unique
    private static void radiance$unpackBitsToBytes(IntBuffer output,
        UnihexProvider.LineData contents, int left, int right) {
        for (int row = 0; row < 16; row++) {
            radiance$unpackBitsToBytes(output, contents.line(row), left, right);
        }
    }

    @Unique
    private static void radiance$unpackBitsToBytes(IntBuffer output, int bits, int left,
        int right) {
        int startBit = 32 - left - 1;
        int endBit = 32 - right - 1;
        for (int bit = startBit; bit >= endBit; bit--) {
            if (bit >= 32 || bit < 0) {
                output.put(0);
            } else {
                output.put(((bits >> bit) & 1) != 0 ? -1 : 0);
            }
        }
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
                throw new IllegalStateException("Cannot access 26.2 unihex glyph field " + fieldName,
                    ex);
            }
        }
        throw new IllegalStateException("Missing 26.2 unihex glyph field " + fieldName);
    }
}
