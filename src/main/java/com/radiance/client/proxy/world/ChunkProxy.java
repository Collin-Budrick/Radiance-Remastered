package com.radiance.client.proxy.world;

import com.mojang.blaze3d.vertex.MeshData;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.constant.Constants;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IViewAreaExt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class ChunkProxy {

    public static final SectionMesh PROCESSED = new MarkerSectionMesh();
    public static final SectionMesh TERRAIN_EMPTY = new MarkerSectionMesh();

    private static final Map<Integer, RebuildEntry> rebuildQueue =
        new ConcurrentHashMap<>();
    private static final Set<Integer> forcedRebuildIndices = ConcurrentHashMap.newKeySet();
    private static ViewArea currentStorage;
    private static volatile boolean nativeChunkStorageInitialized;
    private static volatile boolean pendingRebuildAll;
    private static volatile boolean loggedMissingRegion;
    private static volatile boolean loggedMissingBlockAtlasTexture;
    private static volatile boolean loggedChunkTextureRepair;
    private static final Set<ChunkSectionLayer> loggedChunkVertexStatsLayers =
        ConcurrentHashMap.newKeySet();
    private static int uploadedSectionCount;

    public static int builtChunkNum = 0;

    public static native void initNative(int numChunks, int sizeX, int sizeY, int sizeZ,
        int bottomSectionCoord);

    public static void init(int numChunks, int sizeX, int sizeY, int sizeZ, int bottomSectionCoord) {
        nativeChunkStorageInitialized = false;
        initNative(numChunks, sizeX, sizeY, sizeZ, bottomSectionCoord);
        nativeChunkStorageInitialized = true;
    }

    private static native void updateSectionPosNative(int x, int y, int z);

    public static void updateSectionPos(SectionPos sectionPos) {
        if (!nativeChunkStorageInitialized) {
            return;
        }
        updateSectionPosNative(sectionPos.x(), sectionPos.y(), sectionPos.z());
    }

    public static void setStorage(ViewArea storage) {
        currentStorage = storage;
        if (currentStorage != null && pendingRebuildAll) {
            pendingRebuildAll = false;
            queueRebuildAll(currentStorage);
        }
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        return () -> {
        };
    }

    public static void clear() {
        rebuildQueue.clear();
        forcedRebuildIndices.clear();
        currentStorage = null;
        builtChunkNum = 0;
        nativeChunkStorageInitialized = false;
        pendingRebuildAll = false;
        loggedMissingRegion = false;
        loggedChunkVertexStatsLayers.clear();
        clearNative();
    }

    public static void clearNative() {
    }

    public static void rebuildAll() {
        if (currentStorage == null) {
            pendingRebuildAll = true;
            return;
        }

        queueRebuildAll(currentStorage);
    }

    public static void enqueueRebuild(SectionRenderDispatcher.RenderSection chunk) {
        rebuildQueue.compute(chunk.index, (index, existing) -> new RebuildEntry(chunk,
            existing == null ? null : existing.region));
    }

    public static void enqueueRebuild(SectionRenderDispatcher.RenderSection chunk,
        RenderSectionRegion region) {
        rebuildQueue.put(chunk.index, new RebuildEntry(chunk, region));
    }

    public static void waitImportantChunkRebuild() {
    }

    public static void rebuild(Camera camera) {
        if (!RendererAvailability.isRendererLifecycleActive() || !nativeChunkStorageInitialized
            || camera == null) {
            return;
        }

        if (currentStorage != null && pendingRebuildAll) {
            pendingRebuildAll = false;
            queueRebuildAll(currentStorage);
        }

        List<RebuildEntry> entries = new ArrayList<>(rebuildQueue.values());
        if (entries.isEmpty()) {
            return;
        }

        BlockPos cameraBlock = camera.blockPosition();
        int rebuilt = 0;
        int skippedMissingRegion = 0;
        for (RebuildEntry entry : entries) {
            if (entry == null || entry.section == null) {
                continue;
            }
            RenderSectionRegion region = entry.region;
            if (region == null) {
                skippedMissingRegion++;
                continue;
            }

            boolean important = forcedRebuildIndices.remove(entry.section.index)
                || isImportant(entry.section, cameraBlock);
            try {
                entry.section.compileSync(region);
                rebuildQueue.remove(entry.section.index);
                rebuilt++;
            } catch (RuntimeException exception) {
                RadianceClient.LOGGER.warn(
                    "Radiance chunk bridge: failed to rebuild native section index={}",
                    entry.section.index, exception);
            }
        }

        if (skippedMissingRegion > 0 && !loggedMissingRegion) {
            loggedMissingRegion = true;
            RadianceClient.LOGGER.info(
                "Radiance chunk bridge: {} queued sections are waiting for a 26.2 RenderSectionRegion",
                skippedMissingRegion);
        }
        if (rebuilt > 0) {
            RadianceClient.LOGGER.info(
                "Radiance chunk bridge: submitted {} queued section rebuilds to the 26.2 compiler",
                rebuilt);
        }
    }

    public static void uploadCompiledSection(SectionPos sectionPos, SectionCompiler.Results results) {
        if (!RendererAvailability.isRendererLifecycleActive() || currentStorage == null
            || results == null || results.renderedLayers.isEmpty()) {
            return;
        }

        BlockPos origin = sectionPos.origin();
        SectionRenderDispatcher.RenderSection section = currentStorage.getRenderSectionAt(origin);
        if (section == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int blockAtlasTextureId = blockAtlasTextureId();
            int geometryCount = 0;
            for (MeshData meshData : results.renderedLayers.values()) {
                if (meshData != null && meshData.drawState().vertexCount() > 0) {
                    geometryCount++;
                }
            }
            if (geometryCount == 0) {
                invalidateSingle(section.index);
                return;
            }

            IntBuffer geometryTypes = stack.mallocInt(geometryCount);
            LongBuffer geometryGroupNames = stack.mallocLong(geometryCount);
            IntBuffer geometryTextures = stack.mallocInt(geometryCount);
            IntBuffer vertexFormats = stack.mallocInt(geometryCount);
            IntBuffer vertexCounts = stack.mallocInt(geometryCount);
            LongBuffer vertexAddrs = stack.mallocLong(geometryCount);

            int geometryIndex = 0;
            for (Map.Entry<ChunkSectionLayer, MeshData> entry : results.renderedLayers.entrySet()) {
                MeshData meshData = entry.getValue();
                if (meshData == null || meshData.drawState().vertexCount() <= 0) {
                    continue;
                }

                ByteBuffer vertexBuffer = meshData.vertexBuffer();
                int repairedTextures = repairChunkTextureIds(vertexBuffer,
                    meshData.drawState().vertexCount(), blockAtlasTextureId);
                logChunkVertexStats(entry.getKey(), vertexBuffer,
                    meshData.drawState().vertexCount(), blockAtlasTextureId);
                if (repairedTextures > 0 && !loggedChunkTextureRepair) {
                    loggedChunkTextureRepair = true;
                    RadianceClient.LOGGER.info(
                        "Radiance chunk bridge: repaired {} PBR chunk vertices with block atlas native texture id {}",
                        repairedTextures, blockAtlasTextureId);
                }

                geometryTypes.put(geometryIndex, geometryType(entry.getKey()));
                geometryGroupNames.put(geometryIndex,
                    MemoryUtil.memAddress(stack.UTF8(entry.getKey().name())));
                geometryTextures.put(geometryIndex, blockAtlasTextureId);
                vertexFormats.put(geometryIndex, Constants.DefaultVertexFormat.PBR_TRIANGLE.getValue());
                vertexCounts.put(geometryIndex, meshData.drawState().vertexCount());
                vertexAddrs.put(geometryIndex, MemoryUtil.memAddress(vertexBuffer));
                geometryIndex++;
            }

            rebuildSingle(origin.getX(), origin.getY(), origin.getZ(), section.index,
                geometryCount, MemoryUtil.memAddress(geometryTypes),
                MemoryUtil.memAddress(geometryGroupNames), MemoryUtil.memAddress(geometryTextures),
                MemoryUtil.memAddress(vertexFormats), MemoryUtil.memAddress(vertexCounts),
                MemoryUtil.memAddress(vertexAddrs), false);
            builtChunkNum++;
            uploadedSectionCount++;
            if (uploadedSectionCount <= 16 || uploadedSectionCount % 128 == 0) {
                RadianceClient.LOGGER.info(
                    "Radiance chunk bridge: uploaded native section index={} origin=({}, {}, {}) geometries={} totalUploaded={}",
                    section.index, origin.getX(), origin.getY(), origin.getZ(), geometryCount,
                    uploadedSectionCount);
            }
        }
    }

    private static int geometryType(ChunkSectionLayer layer) {
        return layer == ChunkSectionLayer.SOLID
            ? Constants.GeometryTypes.WORLD_SOLID.getValue()
            : Constants.GeometryTypes.WORLD_TRANSPARENT.getValue();
    }

    private static int blockAtlasTextureId() {
        Integer id = TextureTracker.textureID2GLID.get(TextureAtlas.LOCATION_BLOCKS);
        if (id == null || id == 0) {
            if (!loggedMissingBlockAtlasTexture) {
                loggedMissingBlockAtlasTexture = true;
                RadianceClient.LOGGER.warn(
                    "Radiance chunk bridge: block atlas native texture id is not available yet");
            }
            return 0;
        }
        return id;
    }

    private static int repairChunkTextureIds(ByteBuffer vertexBuffer, int vertexCount,
        int textureId) {
        if (textureId == 0 || vertexBuffer == null || vertexCount <= 0) {
            return 0;
        }

        int stride = Constants.DefaultVertexFormat.PBR_TRIANGLE.getVertexFormat().getVertexSize();
        int useTextureOffset = PBRVertexFormatElements.PBR_USE_TEXTURE.offset();
        int textureIdOffset = PBRVertexFormatElements.PBR_TEXTURE_ID.offset();
        long baseAddress = MemoryUtil.memAddress(vertexBuffer);
        int repaired = 0;
        for (int i = 0; i < vertexCount; i++) {
            long vertexAddress = baseAddress + (long) i * stride;
            int useTexture = MemoryUtil.memGetInt(vertexAddress + useTextureOffset);
            int currentTextureId = MemoryUtil.memGetInt(vertexAddress + textureIdOffset);
            if (useTexture != 0 && currentTextureId == 0) {
                MemoryUtil.memPutInt(vertexAddress + textureIdOffset, textureId);
                repaired++;
            }
        }
        return repaired;
    }

    private static void logChunkVertexStats(ChunkSectionLayer layer, ByteBuffer vertexBuffer,
        int vertexCount, int blockAtlasTextureId) {
        if (vertexBuffer == null || vertexCount <= 0
            || !loggedChunkVertexStatsLayers.add(layer)) {
            return;
        }

        int stride = Constants.DefaultVertexFormat.PBR_TRIANGLE.getVertexFormat().getVertexSize();
        int useTextureOffset = PBRVertexFormatElements.PBR_USE_TEXTURE.offset();
        int textureIdOffset = PBRVertexFormatElements.PBR_TEXTURE_ID.offset();
        int useColorOffset = PBRVertexFormatElements.PBR_USE_COLOR_LAYER.offset();
        int useLightOffset = PBRVertexFormatElements.PBR_USE_LIGHT.offset();
        int lightUvOffset = PBRVertexFormatElements.PBR_LIGHT_UV.offset();
        int textureUvOffset = PBRVertexFormatElements.PBR_TEXTURE_UV.offset();
        int colorLayerOffset = PBRVertexFormatElements.PBR_COLOR_LAYER.offset();
        int posOffset = PBRVertexFormatElements.PBR_POS.offset();
        int alphaModeOffset = PBRVertexFormatElements.PBR_POST_BASE.offset()
            + 3 * Float.BYTES;
        long baseAddress = MemoryUtil.memAddress(vertexBuffer);
        int textured = 0;
        int colored = 0;
        int lit = 0;
        int minAlphaMode = Integer.MAX_VALUE;
        int maxAlphaMode = Integer.MIN_VALUE;
        int firstAlphaMode = 0;
        int zeroTextureIds = 0;
        int minTextureId = Integer.MAX_VALUE;
        int maxTextureId = Integer.MIN_VALUE;
        int minLightU = Integer.MAX_VALUE;
        int maxLightU = Integer.MIN_VALUE;
        int minLightV = Integer.MAX_VALUE;
        int maxLightV = Integer.MIN_VALUE;
        float firstU = 0.0F;
        float firstV = 0.0F;
        float firstR = 0.0F;
        float firstG = 0.0F;
        float firstB = 0.0F;
        float firstA = 0.0F;
        float minColor = Float.POSITIVE_INFINITY;
        float maxColor = Float.NEGATIVE_INFINITY;
        int firstTextureId = 0;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        float[] firstQuadPositions = new float[12];

        for (int i = 0; i < vertexCount; i++) {
            long vertexAddress = baseAddress + (long) i * stride;
            float px = MemoryUtil.memGetFloat(vertexAddress + posOffset);
            float py = MemoryUtil.memGetFloat(vertexAddress + posOffset + Float.BYTES);
            float pz = MemoryUtil.memGetFloat(vertexAddress + posOffset + 2L * Float.BYTES);
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            minZ = Math.min(minZ, pz);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
            maxZ = Math.max(maxZ, pz);
            if (i < 4) {
                int positionIndex = i * 3;
                firstQuadPositions[positionIndex] = px;
                firstQuadPositions[positionIndex + 1] = py;
                firstQuadPositions[positionIndex + 2] = pz;
            }
            int useTexture = MemoryUtil.memGetInt(vertexAddress + useTextureOffset);
            int textureId = MemoryUtil.memGetInt(vertexAddress + textureIdOffset);
            if (i == 0) {
                firstTextureId = textureId;
                firstAlphaMode = MemoryUtil.memGetInt(vertexAddress + alphaModeOffset);
                firstU = MemoryUtil.memGetFloat(vertexAddress + textureUvOffset);
                firstV = MemoryUtil.memGetFloat(vertexAddress + textureUvOffset + Float.BYTES);
                firstR = MemoryUtil.memGetFloat(vertexAddress + colorLayerOffset);
                firstG = MemoryUtil.memGetFloat(vertexAddress + colorLayerOffset + Float.BYTES);
                firstB = MemoryUtil.memGetFloat(vertexAddress + colorLayerOffset + 2L * Float.BYTES);
                firstA = MemoryUtil.memGetFloat(vertexAddress + colorLayerOffset + 3L * Float.BYTES);
            }
            if (useTexture != 0) {
                textured++;
                if (textureId == 0) {
                    zeroTextureIds++;
                } else {
                    minTextureId = Math.min(minTextureId, textureId);
                    maxTextureId = Math.max(maxTextureId, textureId);
                }
            }
            if (MemoryUtil.memGetInt(vertexAddress + useColorOffset) != 0) {
                colored++;
                for (int component = 0; component < 4; component++) {
                    float value = MemoryUtil.memGetFloat(vertexAddress + colorLayerOffset
                        + (long) component * Float.BYTES);
                    minColor = Math.min(minColor, value);
                    maxColor = Math.max(maxColor, value);
                }
            }
            if (MemoryUtil.memGetInt(vertexAddress + useLightOffset) != 0) {
                lit++;
                int lightU = MemoryUtil.memGetInt(vertexAddress + lightUvOffset);
                int lightV = MemoryUtil.memGetInt(vertexAddress + lightUvOffset + Integer.BYTES);
                minLightU = Math.min(minLightU, lightU);
                maxLightU = Math.max(maxLightU, lightU);
                minLightV = Math.min(minLightV, lightV);
                maxLightV = Math.max(maxLightV, lightV);
            }
            int alphaMode = MemoryUtil.memGetInt(vertexAddress + alphaModeOffset);
            minAlphaMode = Math.min(minAlphaMode, alphaMode);
            maxAlphaMode = Math.max(maxAlphaMode, alphaMode);
        }

        RadianceClient.LOGGER.info(
            "Radiance chunk bridge: first PBR vertex stats layer={} vertices={} posRange=({}, {}, {})..({}, {}, {}) firstQuad=({}, {}, {})|({}, {}, {})|({}, {}, {})|({}, {}, {}) textured={} zeroTextureIds={} textureIdRange={}..{} blockAtlasTextureId={} alphaModeRange={}..{} firstAlphaMode={} colored={} colorRange={}..{} firstColor=({}, {}, {}, {}) lit={} lightRange=({}, {})..({}, {}) firstTextureId={} firstUv=({}, {})",
            layer, vertexCount,
            minX == Float.POSITIVE_INFINITY ? 0.0F : minX,
            minY == Float.POSITIVE_INFINITY ? 0.0F : minY,
            minZ == Float.POSITIVE_INFINITY ? 0.0F : minZ,
            maxX == Float.NEGATIVE_INFINITY ? 0.0F : maxX,
            maxY == Float.NEGATIVE_INFINITY ? 0.0F : maxY,
            maxZ == Float.NEGATIVE_INFINITY ? 0.0F : maxZ,
            firstQuadPositions[0], firstQuadPositions[1], firstQuadPositions[2],
            firstQuadPositions[3], firstQuadPositions[4], firstQuadPositions[5],
            firstQuadPositions[6], firstQuadPositions[7], firstQuadPositions[8],
            firstQuadPositions[9], firstQuadPositions[10], firstQuadPositions[11],
            textured, zeroTextureIds,
            minTextureId == Integer.MAX_VALUE ? 0 : minTextureId,
            maxTextureId == Integer.MIN_VALUE ? 0 : maxTextureId,
            blockAtlasTextureId,
            minAlphaMode == Integer.MAX_VALUE ? 0 : minAlphaMode,
            maxAlphaMode == Integer.MIN_VALUE ? 0 : maxAlphaMode,
            firstAlphaMode, colored,
            minColor == Float.POSITIVE_INFINITY ? 0.0F : minColor,
            maxColor == Float.NEGATIVE_INFINITY ? 0.0F : maxColor,
            firstR, firstG, firstB, firstA, lit,
            minLightU == Integer.MAX_VALUE ? 0 : minLightU,
            minLightV == Integer.MAX_VALUE ? 0 : minLightV,
            maxLightU == Integer.MIN_VALUE ? 0 : maxLightU,
            maxLightV == Integer.MIN_VALUE ? 0 : maxLightV,
            firstTextureId, firstU, firstV);
    }

    private static native void rebuildSingleNative(int originX, int originY, int originZ, long index,
        int geometryCount, long geometryTypes, long geometryGroupNames, long geometryTextures,
        long vertexFormats, long vertexCounts, long vertexAddrs, boolean important);

    public static void rebuildSingle(int originX, int originY, int originZ, long index,
        int geometryCount, long geometryTypes, long geometryGroupNames, long geometryTextures,
        long vertexFormats, long vertexCounts, long vertexAddrs, boolean important) {
        if (!nativeChunkStorageInitialized) {
            return;
        }
        rebuildSingleNative(originX, originY, originZ, index, geometryCount, geometryTypes,
            geometryGroupNames, geometryTextures, vertexFormats, vertexCounts, vertexAddrs, important);
    }

    private static native void relocateSingleNative(long index, int x, int y, int z);

    public static void relocateSingle(long index, int x, int y, int z) {
        if (!nativeChunkStorageInitialized) {
            return;
        }
        relocateSingleNative(index, x, y, z);
    }

    private static native void invalidateSingleNative(long index);

    public static void invalidateSingle(long index) {
        if (!nativeChunkStorageInitialized) {
            return;
        }
        invalidateSingleNative(index);
    }

    private static native boolean isChunkReadyNative(long index);

    public static boolean isChunkReady(long index) {
        return nativeChunkStorageInitialized && isChunkReadyNative(index);
    }

    public static boolean isChunkReady(SectionRenderDispatcher.RenderSection builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    private static void queueRebuildAll(ViewArea storage) {
        Collection<SectionRenderDispatcher.RenderSection> sections = sections(storage);
        if (sections.isEmpty()) {
            pendingRebuildAll = true;
            return;
        }

        for (SectionRenderDispatcher.RenderSection section : sections) {
            if (section == null) {
                continue;
            }
            forcedRebuildIndices.add(section.index);
            enqueueRebuild(section);
        }
    }

    private static Collection<SectionRenderDispatcher.RenderSection> sections(ViewArea storage) {
        if (!(storage instanceof IViewAreaExt ext)) {
            return List.of();
        }

        RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections =
            ext.radiance$getSections();
        if (sections == null) {
            return List.of();
        }

        List<SectionRenderDispatcher.RenderSection> result = new ArrayList<>(sections.size());
        sections.forEach(result::add);
        return result;
    }

    private static boolean isImportant(SectionRenderDispatcher.RenderSection section,
        BlockPos cameraBlock) {
        BlockPos origin = section.getRenderOrigin();
        double dx = origin.getX() + 8.0D - cameraBlock.getX();
        double dy = origin.getY() + 8.0D - cameraBlock.getY();
        double dz = origin.getZ() + 8.0D - cameraBlock.getZ();
        return dx * dx + dy * dy + dz * dz < 768.0D;
    }

    private static final class RebuildEntry {

        private final SectionRenderDispatcher.RenderSection section;
        private final RenderSectionRegion region;

        private RebuildEntry(SectionRenderDispatcher.RenderSection section,
            RenderSectionRegion region) {
            this.section = section;
            this.region = region;
        }
    }

    private static final class MarkerSectionMesh implements SectionMesh {

        @Override
        public boolean facesCanSeeEachother(Direction from, Direction to) {
            return false;
        }
    }
}
