package com.radiance.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.RadianceClient;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.Identifier;

public final class TextureUploadReplay {

    private static final Map<GpuTexture, List<CapturedUpload>> PENDING_UPLOADS =
        new ConcurrentHashMap<>();
    private static final AtomicBoolean LOGGED_CAPTURE = new AtomicBoolean(false);

    private TextureUploadReplay() {
    }

    public static void capture(GpuTexture texture, Identifier identifier, NativeImage image,
        int mipLevel, int dstX, int dstY) {
        if (texture == null || image == null) {
            return;
        }

        NativeImage copied = null;
        try {
            copied = new NativeImage(image.format(), image.getWidth(), image.getHeight(), false);
            copied.copyFrom(image);
            com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt copiedExt =
                (com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt) (Object) copied;
            copiedExt.radiance$setIdentifier(identifier);

            TextureTracker.rememberTextureIdentifier(identifier, texture);
            PENDING_UPLOADS.computeIfAbsent(texture,
                ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(new CapturedUpload(identifier, copied, mipLevel, dstX, dstY));
            copied = null;

            if (LOGGED_CAPTURE.compareAndSet(false, true)) {
                RadianceClient.LOGGER.info(
                    "Radiance texture upload replay: caching pre-native vanilla texture writes");
            }
        } catch (RuntimeException ex) {
            RadianceClient.LOGGER.warn(
                "Radiance texture upload replay: failed to cache a vanilla texture write", ex);
        } finally {
            if (copied != null) {
                copied.close();
            }
        }
    }

    public static void replayAll() {
        if (!TextureTracker.shouldMirrorTextures() || PENDING_UPLOADS.isEmpty()) {
            return;
        }

        int replayedUploads = 0;
        int replayedTextures = 0;
        int skippedClosedTextures = 0;
        for (Map.Entry<GpuTexture, List<CapturedUpload>> entry : PENDING_UPLOADS.entrySet()) {
            GpuTexture texture = entry.getKey();
            List<CapturedUpload> uploads = PENDING_UPLOADS.remove(texture);
            if (uploads == null || uploads.isEmpty()) {
                continue;
            }

            if (texture.isClosed()) {
                skippedClosedTextures++;
                synchronized (uploads) {
                    for (CapturedUpload upload : uploads) {
                        upload.close();
                    }
                }
                continue;
            }

            boolean allowSmallTexture = false;
            synchronized (uploads) {
                for (CapturedUpload upload : uploads) {
                    allowSmallTexture |= TextureTracker.shouldAllowSmallTexture(upload.identifier,
                        texture);
                }
            }
            int targetId = allowSmallTexture
                ? TextureTracker.getOrRegisterGuiTextureHandle(texture)
                : TextureTracker.getOrRegisterTextureHandle(texture);
            if (targetId != 0) {
                replayedTextures++;
            }

            synchronized (uploads) {
                for (CapturedUpload upload : uploads) {
                    try {
                        if (targetId != 0) {
                            upload.queue(texture, targetId);
                            replayedUploads++;
                        }
                    } finally {
                        upload.close();
                    }
                }
            }
        }

        if (replayedUploads > 0) {
            RadianceClient.LOGGER.info(
                "Radiance texture upload replay: replayed {} vanilla texture writes into {} native textures; skipped {} closed temporary textures",
                replayedUploads, replayedTextures, skippedClosedTextures);
        }
    }

    private record CapturedUpload(Identifier identifier, NativeImage image, int mipLevel, int dstX,
                                  int dstY) {

        private void queue(GpuTexture texture, int targetId) {
            if (identifier != null) {
                TextureTracker.rememberTextureHandle(identifier, targetId);
            }

            AuxiliaryTextures.loadAndUpload(image, texture, mipLevel, dstX, dstY);

            long pointer = ((INativeImageExt) (Object) image).radiance$getPointer();
            int srcSizeInBytes = image.getWidth() * image.getHeight() * image.format().components();
            TextureProxy.queueUpload(pointer, srcSizeInBytes, image.getWidth(), targetId, 0, 0,
                dstX, dstY, image.getWidth(), image.getHeight(), mipLevel);
        }

        private void close() {
            image.close();
        }
    }
}
