package com.radiance.client.proxy.vulkan;

import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.RendererAvailability;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public class BufferProxy {

    private static final GpuBufferMirrorRegistry GPU_BUFFER_MIRRORS =
        new GpuBufferMirrorRegistry();
    private static final Object WRITE_MAPPED_VIEW_LOCK = new Object();
    private static final ReferenceQueue<GpuBufferSlice.MappedView> WRITE_MAPPED_VIEW_QUEUE =
        new ReferenceQueue<>();
    private static final Map<WeakMappedViewKey, Boolean> WRITE_MAPPED_VIEWS = new HashMap<>();

    public static native int allocateBuffer();

    public static native void initializeBuffer(int id, int size, int usageFlags);

    public static native void buildIndexBuffer(int id, int type, int drawMode, int vertexCount,
        int expectedIndexCount);

    public static native void queueUpload(long ptr, int dstId);

    public static native void queueUploadRange(long ptr, int size, int dstId, int dstOffset);

    public static NativeBufferMirror registerNativeBufferMirror(GpuBuffer buffer, int nativeId,
        long nativeSerial, String source) {
        return GPU_BUFFER_MIRRORS.registerNative(buffer, nativeId, nativeSerial, source);
    }

    public static NativeBufferMirror lookupNativeBufferMirror(GpuBuffer buffer) {
        return GPU_BUFFER_MIRRORS.lookup(buffer);
    }

    public static byte[] copyMirroredBufferRange(GpuBuffer buffer, long offset, int size) {
        return GPU_BUFFER_MIRRORS.copyRange(buffer, offset, size);
    }

    public static byte[] copyMirroredBufferRange(GpuBufferSlice slice, int size) {
        if (slice == null || slice.buffer() == null || size < 0 || slice.length() < size) {
            return null;
        }
        return copyMirroredBufferRange(slice.buffer(), slice.offset(), size);
    }

    public static byte[] copyMirroredBufferContent(GpuBuffer buffer) {
        return GPU_BUFFER_MIRRORS.copyFullContent(buffer);
    }

    public static NativeBufferMirror resolveNativeBufferMirror(GpuBuffer buffer) {
        NativeBufferMirror mirror = GPU_BUFFER_MIRRORS.lookup(buffer);
        if (mirror.isMissing() || mirror.hasNativeId()) {
            return mirror;
        }
        if (!mirror.contentAvailable()) {
            return mirror;
        }

        byte[] content = GPU_BUFFER_MIRRORS.copyFullContent(buffer);
        if (content == null) {
            return GPU_BUFFER_MIRRORS.markFallback(buffer,
                "CPU backing unavailable for deferred native upload",
                "resolveNativeBufferMirror");
        }

        int nativeId = ensureNativeBufferMirror(buffer, "resolveNativeBufferMirror");
        if (nativeId < 0) {
            return GPU_BUFFER_MIRRORS.lookup(buffer);
        }
        if (!queueUploadRange(content, nativeId, 0)) {
            return GPU_BUFFER_MIRRORS.markFallback(buffer,
                "native range upload failed during deferred resolve",
                "resolveNativeBufferMirror");
        }
        return GPU_BUFFER_MIRRORS.markNativeRangeUploaded(buffer, 0L, content.length,
            "resolveNativeBufferMirror");
    }

    public static NativeBufferMirror invalidateNativeBufferMirror(GpuBuffer buffer,
        String reason) {
        return GPU_BUFFER_MIRRORS.invalidate(buffer, reason);
    }

    public static NativeBufferMirror markNativeBufferMirrorFallback(GpuBuffer buffer,
        String reason) {
        return GPU_BUFFER_MIRRORS.markFallback(buffer, reason, "fallback");
    }

    public static NativeBufferMirror mirrorCreatedGpuBuffer(GpuBuffer buffer, int usage,
        long size, ByteBuffer initialData, String source) {
        if (buffer == null) {
            return NativeBufferMirror.missing();
        }

        GPU_BUFFER_MIRRORS.observe(buffer, size, usage, source);
        if (initialData == null || !initialData.hasRemaining()) {
            return GPU_BUFFER_MIRRORS.lookup(buffer);
        }
        return mirrorUpload(buffer, 0L, initialData, source);
    }

    public static NativeBufferMirror mirrorWriteToBuffer(GpuBufferSlice target,
        ByteBuffer source) {
        if (target == null || target.buffer() == null || source == null) {
            return NativeBufferMirror.missing();
        }
        if (!source.hasRemaining()) {
            return GPU_BUFFER_MIRRORS.lookup(target.buffer());
        }
        return mirrorUpload(target.buffer(), target.offset(), source,
            "CommandEncoder.writeToBuffer");
    }

    public static void trackMappedView(GpuBufferSlice.MappedView view, boolean write) {
        if (view == null) {
            return;
        }
        synchronized (WRITE_MAPPED_VIEW_LOCK) {
            expungeStaleMappedViews();
            WeakMappedViewKey lookup = WeakMappedViewKey.lookup(view);
            if (write) {
                WRITE_MAPPED_VIEWS.put(WeakMappedViewKey.weak(view), Boolean.TRUE);
            } else {
                WRITE_MAPPED_VIEWS.remove(lookup);
            }
        }
    }

    public static NativeBufferMirror mirrorMappedView(GpuBufferSlice.MappedView view) {
        if (view == null) {
            return NativeBufferMirror.missing();
        }
        GpuBufferSlice slice = view.slice();
        if (slice == null || slice.buffer() == null) {
            return NativeBufferMirror.missing();
        }
        if (!consumeMappedViewWriteFlag(view)) {
            return GPU_BUFFER_MIRRORS.lookup(slice.buffer());
        }
        ByteBuffer data = view.data();
        if (data == null) {
            return GPU_BUFFER_MIRRORS.lookup(slice.buffer());
        }
        if (slice.length() < 0L || slice.length() > Integer.MAX_VALUE) {
            return GPU_BUFFER_MIRRORS.markFallback(slice.buffer(),
                "mapped view range is outside the Java mirrorable range",
                "GpuBufferSlice.MappedView.close");
        }

        int size = (int) Math.min((long) data.capacity(), slice.length());
        if (size <= 0) {
            return GPU_BUFFER_MIRRORS.lookup(slice.buffer());
        }

        ByteBuffer uploadView = data.duplicate();
        uploadView.clear();
        uploadView.limit(size);
        return mirrorUpload(slice.buffer(), slice.offset(), uploadView,
            "GpuBufferSlice.MappedView.close");
    }

    private static boolean consumeMappedViewWriteFlag(GpuBufferSlice.MappedView view) {
        synchronized (WRITE_MAPPED_VIEW_LOCK) {
            expungeStaleMappedViews();
            return WRITE_MAPPED_VIEWS.remove(WeakMappedViewKey.lookup(view)) != null;
        }
    }

    private static void expungeStaleMappedViews() {
        WeakMappedViewKey key;
        while ((key = (WeakMappedViewKey) WRITE_MAPPED_VIEW_QUEUE.poll()) != null) {
            WRITE_MAPPED_VIEWS.remove(key);
        }
    }

    public static NativeBufferMirror mirrorCopyToBuffer(GpuBufferSlice source,
        GpuBufferSlice target) {
        if (source == null || target == null || target.buffer() == null) {
            return NativeBufferMirror.missing();
        }
        if (source.length() < 0L || source.length() > Integer.MAX_VALUE) {
            return GPU_BUFFER_MIRRORS.markFallback(target.buffer(),
                "copyToBuffer source range is outside the Java mirrorable range",
                "CommandEncoder.copyToBuffer");
        }

        int size = (int) source.length();
        if (size == 0) {
            return GPU_BUFFER_MIRRORS.lookup(target.buffer());
        }
        if (target.length() < size) {
            return GPU_BUFFER_MIRRORS.markFallback(target.buffer(),
                "copyToBuffer target range is smaller than the source range",
                "CommandEncoder.copyToBuffer");
        }

        byte[] bytes = GPU_BUFFER_MIRRORS.copyRange(source.buffer(), source.offset(), size);
        if (bytes == null) {
            return GPU_BUFFER_MIRRORS.markFallback(target.buffer(),
                "copyToBuffer source content is not CPU-mirrored",
                "CommandEncoder.copyToBuffer");
        }

        NativeBufferMirror mirror = GPU_BUFFER_MIRRORS.recordCpuWrite(target.buffer(),
            target.offset(), bytes, "CommandEncoder.copyToBuffer");
        int nativeId = ensureNativeBufferMirror(target.buffer(), "CommandEncoder.copyToBuffer");
        if (nativeId < 0) {
            return mirror;
        }
        if (!queueUploadRange(bytes, nativeId, target.offset())) {
            return GPU_BUFFER_MIRRORS.markFallback(target.buffer(),
                "native range upload failed while replaying copyToBuffer",
                "CommandEncoder.copyToBuffer");
        }
        return GPU_BUFFER_MIRRORS.markNativeRangeUploaded(target.buffer(), target.offset(),
            size, "CommandEncoder.copyToBuffer");
    }

    public static BufferInfo getBufferInfo(ByteBuffer buf) {
        ByteBuffer b = buf.slice();

        assert b.isDirect();

        long addr = memAddress(b);
        int size = b.remaining();
        return new BufferInfo(buf, addr, size);
    }

    private static void queueUpload(ByteBuffer buf, int expectedSize, int dstId) {
        BufferInfo bufferInfo = getBufferInfo(buf);
        assert bufferInfo.size == expectedSize;
        queueUpload(bufferInfo.addr, dstId);
    }

    public static native void performQueuedUpload();

    public static VertexIndexBufferHandle createAndUploadVertexIndexBuffer(
        MeshData builtBuffer) {
        MeshData.DrawState drawState = builtBuffer.drawState();
        assert drawState.primitiveTopology() == PrimitiveTopology.QUADS;

        int vertexSize = drawState.vertexCount() * drawState.format().getVertexSize();
        int vertexId = allocateBuffer();
        initializeBuffer(vertexId, vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        queueUpload(builtBuffer.vertexBuffer(), vertexSize, vertexId);

        int indexSize = drawState.indexCount() * drawState.indexType().bytes;
        int indexId = allocateBuffer();
        initializeBuffer(indexId, indexSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        if (builtBuffer.indexBuffer() != null) {
            queueUpload(builtBuffer.indexBuffer(), indexSize, indexId);
        } else {
            int type = indexTypeValue(drawState.indexType());
            int drawMode = primitiveTopologyValue(drawState.primitiveTopology());
            buildIndexBuffer(indexId, type, drawMode, drawState.vertexCount(),
                drawState.indexCount());
        }

        return new VertexIndexBufferHandle(vertexId, indexId);
    }

    public static native void updateOverlayPostUniform(long ptr);

    public static void updateOverlayPostUniform(float radius) {
        try (MemoryStack stack = stackPush()) {
            int size = 96;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            float[] blurDir = {1.0f, 1.0f};
            for (int i = 0; i < 2; i++) {
                bb.putFloat(baseAddr, blurDir[i]);
                baseAddr += Float.BYTES;
            }

            bb.putFloat(baseAddr, radius);
            baseAddr += Float.BYTES;

            float radiusMultiplier = 1.0f;
            bb.putFloat(baseAddr, radiusMultiplier);

            updateOverlayPostUniform(addr);
        }
    }

    public static native void updateWorldUniform(long ptr);

    public static void updateWorldUniform(Camera camera, Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix, Matrix4f projectionMatrix, int overlayTextureID, FogData fog,
        ClientLevel world, int endSkyTextureID, int endPortalTextureID, int lightMapTextureID) {
        try (MemoryStack stack = stackPush()) {
            int size = 592;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            viewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            effectedViewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            baseAddr += Float.BYTES * 16 * 3; // skip the inverse
            baseAddr += Float.BYTES * 2; // skip the jitter

            float gameTime = 0.0F;
            if (world != null) {
                gameTime = (world.getLevelData().getGameTime() % 24000L) / 24000.0F;
            }
            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            baseAddr += Integer.BYTES; // skip seed

            Matrix4f textureMat = new Matrix4f();
            textureMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            bb.putInt(baseAddr, overlayTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, camera.isDetached() ? 0 : 1);
            baseAddr += Integer.BYTES;
            float fogStart = fog == null ? 0.0F : fog.environmentalStart;
            float fogEnd = fog == null ? Float.POSITIVE_INFINITY : fog.environmentalEnd;
            float fogColorR = fog == null ? 0.0F : fog.color.x();
            float fogColorG = fog == null ? 0.0F : fog.color.y();
            float fogColorB = fog == null ? 0.0F : fog.color.z();
            float fogColorA = fog == null ? 0.0F : fog.color.w();

            bb.putFloat(baseAddr, fogStart);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogEnd);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, fogColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColorB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fogColorA);
            baseAddr += Float.BYTES;

            bb.putInt(baseAddr, 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, getSkyType(world));
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;

            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkGridInfo
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos
            baseAddr += Integer.BYTES; // chunkStorageSectionPos

            bb.putInt(baseAddr, endSkyTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, endPortalTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, lightMapTextureID);
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;

            updateWorldUniform(addr);
        }
    }

    public static native void updateSkyUniform(long ptr);

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizonColorR, float horizonColorG, float horizonColorB, float horizonColorA,
        Vector3f sunDirection, int skyType, boolean sunRisingOrSetting, boolean skyDark,
        boolean hasBlindnessOrDarkness, int submersionType, int moonPhase, float rainGradient,
        int sunTextureID, int moonTextureID) {
        try (MemoryStack stack = stackPush()) {
            int size = 80;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            bb.putFloat(baseAddr, baseColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorB);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, skyType);
            baseAddr += Integer.BYTES;

            bb.putFloat(baseAddr, horizonColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizonColorA);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, sunDirection.x);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.y);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.z);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunRisingOrSetting ? 1 : 0);
            baseAddr += Integer.BYTES;

            bb.putInt(baseAddr, skyDark ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, hasBlindnessOrDarkness ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, submersionType);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonPhase);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, rainGradient);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, 0);

            updateSkyUniform(addr);
        }
    }

    public static native void updateMapping(long ptr);

    public static void updateMapping() {
        try (MemoryStack stack = stackPush()) {
            final int elementCount = 4096;
            int size = elementCount * Integer.BYTES * 3;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, -1, size);
            IntBuffer intView = bb.asIntBuffer();

            for (Map.Entry<Integer, Integer> specularEntry : textureMap("GLID2SpecularGLID").entrySet()) {
                int sourceID = specularEntry.getKey();
                int targetID = specularEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3, targetID);
                } else {
                    throw new RuntimeException(
                        "Specular mapping sourceID " + sourceID + " out of index [0, " + (
                            elementCount - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> normalEntry : textureMap("GLID2NormalGLID").entrySet()) {
                int sourceID = normalEntry.getKey();
                int targetID = normalEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 1, targetID);
                } else {
                    throw new RuntimeException(
                        "Normal mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> flagEntry : textureMap("GLID2FlagGLID").entrySet()) {
                int sourceID = flagEntry.getKey();
                int targetID = flagEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 2, targetID);
                } else {
                    throw new RuntimeException(
                        "Flag mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            updateMapping(addr);
        }
    }

    public static void updateEmission() {
        // Emission tiles are uploaded immediately through TextureProxy during texture upload.
    }

    private static int getSkyType(ClientLevel world) {
        if (world == null) {
            return 0;
        }
        if (world.dimension() == Level.END) {
            return 2;
        }
        return world.dimensionType().hasSkyLight() ? 1 : 0;
    }

    private static int indexTypeValue(IndexType indexType) {
        return switch (indexType) {
            case SHORT -> 0;
            case INT -> 1;
        };
    }

    private static int primitiveTopologyValue(PrimitiveTopology topology) {
        return switch (topology) {
            case LINES -> 0;
            case DEBUG_LINE_STRIP -> 1;
            case DEBUG_LINES -> 2;
            case TRIANGLES -> 4;
            case TRIANGLE_STRIP -> 5;
            case TRIANGLE_FAN -> 6;
            case QUADS -> 7;
            default -> 4;
        };
    }

    private static NativeBufferMirror mirrorUpload(GpuBuffer buffer, long dstOffset,
        ByteBuffer source, String sourceName) {
        if (buffer == null) {
            return NativeBufferMirror.missing();
        }
        if (source == null || !source.hasRemaining()) {
            return GPU_BUFFER_MIRRORS.lookup(buffer);
        }

        int size = source.remaining();
        if (dstOffset < 0L || dstOffset > Integer.MAX_VALUE
            || dstOffset + size > buffer.size()) {
            return GPU_BUFFER_MIRRORS.markFallback(buffer,
                "buffer upload range is outside the native mirrorable range", sourceName);
        }

        ByteBuffer uploadView = source.slice();
        NativeBufferMirror mirror = GPU_BUFFER_MIRRORS.recordCpuWrite(buffer, dstOffset,
            uploadView, sourceName);
        int nativeId = ensureNativeBufferMirror(buffer, sourceName);
        if (nativeId < 0) {
            return mirror;
        }
        if (!queueUploadRange(uploadView, size, nativeId, dstOffset)) {
            return GPU_BUFFER_MIRRORS.markFallback(buffer,
                "native range upload failed", sourceName);
        }
        return GPU_BUFFER_MIRRORS.markNativeRangeUploaded(buffer, dstOffset, size, sourceName);
    }

    private static int ensureNativeBufferMirror(GpuBuffer buffer, String source) {
        if (buffer == null) {
            return -1;
        }

        NativeBufferMirror existing = GPU_BUFFER_MIRRORS.observe(buffer, buffer.size(),
            buffer.usage(), source);
        if (existing.nativeId() >= 0 && existing.nativeSerialAvailable()) {
            return existing.nativeId();
        }
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return -1;
        }
        if (buffer.size() <= 0L || buffer.size() > Integer.MAX_VALUE) {
            GPU_BUFFER_MIRRORS.markFallback(buffer,
                "GpuBuffer size is outside the native mirrorable range", source);
            return -1;
        }

        try {
            int nativeId = allocateBuffer();
            initializeBuffer(nativeId, (int) buffer.size(),
                gpuBufferUsageToVkUsage(buffer.usage()));
            GPU_BUFFER_MIRRORS.registerNative(buffer, nativeId, 0L, source);
            return nativeId;
        } catch (UnsatisfiedLinkError | RuntimeException ex) {
            GPU_BUFFER_MIRRORS.markFallback(buffer,
                "native buffer allocation failed: " + ex.getClass().getSimpleName(), source);
            return -1;
        }
    }

    private static boolean queueUploadRange(byte[] bytes, int nativeId, long dstOffset) {
        if (bytes == null || bytes.length == 0 || nativeId < 0 || dstOffset < 0L
            || dstOffset > Integer.MAX_VALUE) {
            return false;
        }

        return queueUploadRange(ByteBuffer.wrap(bytes), bytes.length, nativeId, dstOffset);
    }

    private static boolean queueUploadRange(ByteBuffer source, int size, int nativeId,
        long dstOffset) {
        if (source == null || size <= 0 || nativeId < 0 || dstOffset < 0L
            || dstOffset > Integer.MAX_VALUE || source.remaining() < size) {
            return false;
        }

        ByteBuffer upload = source.slice();
        upload.limit(size);
        if (!upload.isDirect()) {
            ByteBuffer direct = ByteBuffer.allocateDirect(size);
            direct.put(upload);
            direct.flip();
            upload = direct;
        }
        try {
            queueUploadRange(memAddress(upload), size, nativeId, (int) dstOffset);
            return true;
        } catch (UnsatisfiedLinkError | RuntimeException ex) {
            return false;
        }
    }

    public static int gpuBufferUsageToVkUsage(int usage) {
        int flags = 0;
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_SRC) != 0) {
            flags |= VK_BUFFER_USAGE_TRANSFER_SRC_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST) != 0) {
            flags |= VK_BUFFER_USAGE_TRANSFER_DST_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_VERTEX) != 0) {
            flags |= VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_INDEX) != 0) {
            flags |= VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_UNIFORM) != 0) {
            flags |= VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) {
            flags |= VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_INDIRECT_PARAMETERS) != 0) {
            flags |= VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_MAP_READ) != 0) {
            flags |= VK_BUFFER_USAGE_TRANSFER_SRC_BIT.getValue();
        }
        if ((usage & com.mojang.blaze3d.buffers.GpuBuffer.USAGE_MAP_WRITE) != 0) {
            flags |= VK_BUFFER_USAGE_TRANSFER_DST_BIT.getValue();
        }
        if (flags == 0) {
            flags = VK_BUFFER_USAGE_STORAGE_TEXEL_BUFFER_BIT.getValue();
        }
        return flags;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> textureMap(String fieldName) {
        try {
            Class<?> tracker = Class.forName("com.radiance.client.texture.TextureTracker");
            Field field = tracker.getField(fieldName);
            return (Map<Integer, Integer>) field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return Map.of();
        }
    }

    public record NativeBufferMirror(long mirrorSerial, int identityHash, int nativeId,
                                     boolean nativeIdAvailable, long nativeSerial,
                                     boolean nativeSerialAvailable, long contentSerial,
                                     long size, int usage, boolean contentAvailable,
                                     boolean cpuBackingAvailable, boolean fallback,
                                     String fallbackReason, String source) {

        public static NativeBufferMirror missing() {
            return new NativeBufferMirror(0L, 0, -1, false, 0L, false, 0L, 0L, 0,
                false, false, true, "missing", "missing");
        }

        public boolean hasNativeId() {
            return nativeIdAvailable && nativeId >= 0;
        }

        public boolean isMissing() {
            return mirrorSerial == 0L;
        }
    }

    public record BufferInfo(ByteBuffer buf, long addr, int size) {

    }

    public static class VertexIndexBufferHandle {

        public int vertexId;
        public int indexId;

        public VertexIndexBufferHandle(int vertexId, int indexId) {
            this.vertexId = vertexId;
            this.indexId = indexId;
        }
    }

    private static final class WeakMappedViewKey
        extends WeakReference<GpuBufferSlice.MappedView> {

        private final int hash;

        private WeakMappedViewKey(GpuBufferSlice.MappedView view,
            ReferenceQueue<GpuBufferSlice.MappedView> queue) {
            super(view, queue);
            this.hash = System.identityHashCode(view);
        }

        private static WeakMappedViewKey lookup(GpuBufferSlice.MappedView view) {
            return new WeakMappedViewKey(view, null);
        }

        private static WeakMappedViewKey weak(GpuBufferSlice.MappedView view) {
            return new WeakMappedViewKey(view, WRITE_MAPPED_VIEW_QUEUE);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WeakMappedViewKey other)) {
                return false;
            }
            GpuBufferSlice.MappedView self = get();
            return self != null && self == other.get();
        }
    }
}
