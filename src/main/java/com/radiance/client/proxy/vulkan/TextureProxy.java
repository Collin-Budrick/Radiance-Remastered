package com.radiance.client.proxy.vulkan;

import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.option.Options;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;

public class TextureProxy {

    private record EmissionTileKey(int textureId, long tileKey) {
    }

    private static final Map<EmissionTileKey, Object> emissionTileCache =
        new ConcurrentHashMap<>();

    public synchronized static native int generateTextureId();

    public synchronized static native void prepareImage(int id, int mipLevels, int width,
        int height, int format);

    public static void prepareImage(int id, int mipLevels, int width, int height,
        VulkanConstants.VkFormat format) {
        clearEmissionTiles(id);
        prepareImage(id, mipLevels, width, height, format.getValue());
    }

    public synchronized static native void setFilter(int id, int samplingMode, int mipmapMode);

    public synchronized static native void setClamp(int id, int addressMode);

    public synchronized static native void queueUpload(long srcPointer,
        int srcSizeInBytes,
        int srcRowPixels,
        int dstId,
        int srcOffsetX,
        int srcOffsetY,
        int dstOffsetX,
        int dstOffsetY,
        int width,
        int height,
        int level);

    private synchronized static native void uploadEmissionTileNative(int textureId, long tileKey,
        long cellsPtr, int cellCount);

    public static void uploadEmissionTile(Object tileUpdate) {
        if (tileUpdate == null) {
            return;
        }

        emissionTileCache.put(new EmissionTileKey(intField(tileUpdate, "textureId"),
                longField(tileUpdate, "tileKey")),
            tileUpdate);
        if (!Options.collectChunkEmission) {
            return;
        }

        uploadEmissionTileToNative(tileUpdate);
    }

    public static void flushEmissionTiles() {
        if (!Options.collectChunkEmission) {
            return;
        }

        for (Object tileUpdate : emissionTileCache.values()) {
            uploadEmissionTileToNative(tileUpdate);
        }
    }

    public static boolean hasEmissionTile(int textureId, long tileKey) {
        return emissionTileCache.containsKey(new EmissionTileKey(textureId, tileKey));
    }

    private static void clearEmissionTiles(int textureId) {
        emissionTileCache.keySet().removeIf(key -> key.textureId == textureId);
    }

    private static void uploadEmissionTileToNative(Object tileUpdate) {
        if (tileUpdate == null) {
            return;
        }

        ByteBuffer cellsBuffer = null;
        try {
            Iterable<?> cells = iterableField(tileUpdate, "cells");
            int cellCount = collectionSize(cells);
            long cellsAddr = 0L;
            if (cellCount > 0) {
                cellsBuffer = MemoryUtil.memAlloc(cellCount * 8 * Float.BYTES);
                int base = 0;
                for (Object cell : cells) {
                    cellsBuffer.putFloat(base, floatField(cell, "u0"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "v0"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "u1"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "v1"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "avgEmission"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "avgR"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "avgG"));
                    base += Float.BYTES;
                    cellsBuffer.putFloat(base, floatField(cell, "avgB"));
                    base += Float.BYTES;
                }
                cellsAddr = memAddress(cellsBuffer);
            }

            uploadEmissionTileNative(intField(tileUpdate, "textureId"), longField(tileUpdate,
                "tileKey"), cellsAddr, cellCount);
        } finally {
            if (cellsBuffer != null) {
                MemoryUtil.memFree(cellsBuffer);
            }
        }
    }

    public static void prepareImage(NativeImage.Format internalFormat, int id,
        int mipLevels, int width, int height) {
        switch (internalFormat) {
            case RGBA:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM);
                break;
            case RGB:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM);
                break;
            case LUMINANCE_ALPHA:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM);
                break;
            case LUMINANCE:
                prepareImage(id, mipLevels, width, height,
                    VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM);
                break;
        }
    }

    private static int intField(Object target, String fieldName) {
        return ((Number) fieldValue(target, fieldName)).intValue();
    }

    private static long longField(Object target, String fieldName) {
        return ((Number) fieldValue(target, fieldName)).longValue();
    }

    private static float floatField(Object target, String fieldName) {
        return ((Number) fieldValue(target, fieldName)).floatValue();
    }

    private static Object fieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException("Unable to read " + fieldName + " from " + target, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Iterable<?> iterableField(Object target, String fieldName) {
        return (Iterable<?>) fieldValue(target, fieldName);
    }

    private static int collectionSize(Iterable<?> iterable) {
        if (iterable instanceof java.util.Collection<?> collection) {
            return collection.size();
        }
        int count = 0;
        for (Object ignored : iterable) {
            count++;
        }
        return count;
    }
}
