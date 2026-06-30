package com.radiance.client.proxy.world;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radiance.client.constant.Constants;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public class EntityProxy {

    public static final ConcurrentMap<Class<? extends Particle>, AtomicInteger> PARTICLE_COUNTERS =
        new ConcurrentHashMap<>();
    public static Object postTextVertexConsumerProvider;

    public static native void build();

    private static native void queueBuild(float lineWidth, int coordinate, boolean normalOffset,
        int size, long entityHashCodes, long entityPosXs, long entityPosYs, long entityPosZs,
        long entityRayTracingFlags, long entityPostRenderFlags, long entityPrebuiltBLASs,
        long entityPosts, long entityLayerCounts, long geometryTypes, long geometryGroupNames,
        long geometryContentNames, long geometryTextures, long vertexFormats, long indexFormats,
        long vertexCounts, long vertices);

    public static void queueBuild(EntityRenderDataList entityRenderDataList) {
        queueBuild(entityRenderDataList, true);
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList) {
        queueBuild(entityRenderDataList, false);
    }

    public static void queueEntitiesBuild(Object camera, Iterable<?> renderedEntities,
        Object entityRenderDispatcher,
        Object tickCounter, boolean canDrawEntityOutlines) {
    }

    public static CrumblingRenderData queueBlockEntitiesRebuild(Object chunks,
        List<?> noCullingBlockEntities,
        Object blockBreakingProgressions, Object blockEntityRenderDispatcher, float tickDelta) {
        return new CrumblingRenderData(List.of(), new EntityRenderDataList());
    }

    public static void queueCrumblingRebuild(Object camera, Object blockBreakingProgressions,
        Object blockRenderManager,
        Object world, List<?> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
    }

    public static void queueHandRebuild(Object buffers, float tickDelta,
        Object firstPersonRenderer, float handProjectionScale) {
    }

    public static void queueParticleRebuild(Object camera, float tickDelta, Object frustum) {
    }

    public static void queueTargetBlockOutlineRebuild(Object camera, Object world) {
    }

    public static void queueWeatherBuild(Object weatherRendering, Object worldBorderRendering,
        Object world,
        Object camera,
        int ticks,
        float tickDelta) {
    }

    public static void processWorldEntityRenderData(Object storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Object rayTracingFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        EntityRenderData data = new EntityRenderData(hashCode, entityPosX, entityPosY, entityPosZ,
            rayTracingFlagValue(rayTracingFlag), 0, -1, false);
        collectProviderLayers(storageVertexConsumerProvider, data, reflect);
        if (!data.isEmpty()) {
            entityRenderDataList.add(data);
        }
    }

    public static void processPostEntityRenderData(Object storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Object postRenderFlag,
        EntityRenderDataList entityRenderDataList) {
        EntityRenderData data = new EntityRenderData(hashCode, entityPosX, entityPosY, entityPosZ,
            0, postRenderFlagValue(postRenderFlag), -1, true);
        collectProviderLayers(storageVertexConsumerProvider, data, false);
        if (!data.isEmpty()) {
            entityRenderDataList.add(data);
        }
    }

    public static void processPrebuiltBlasEntityRenderData(Object storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Object rayTracingFlag,
        int prebuiltBLAS,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        EntityRenderData data = new EntityRenderData(hashCode, entityPosX, entityPosY, entityPosZ,
            rayTracingFlagValue(rayTracingFlag), 0, prebuiltBLAS, false);
        collectProviderLayers(storageVertexConsumerProvider, data, reflect);
        if (!data.isEmpty()) {
            entityRenderDataList.add(data);
        }
    }

    private static void queueBuild(EntityRenderDataList entityRenderDataList, boolean closeBuffers) {
        if (entityRenderDataList == null || entityRenderDataList.isEmpty()
            || entityRenderDataList.getTotalLayersCount() == 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            int entityCount = entityRenderDataList.size();
            int layerCount = entityRenderDataList.getTotalLayersCount();

            IntBuffer entityHashCodes = stack.mallocInt(entityCount);
            DoubleBuffer entityPosXs = stack.mallocDouble(entityCount);
            DoubleBuffer entityPosYs = stack.mallocDouble(entityCount);
            DoubleBuffer entityPosZs = stack.mallocDouble(entityCount);
            IntBuffer entityRayTracingFlags = stack.mallocInt(entityCount);
            IntBuffer entityPostRenderFlags = stack.mallocInt(entityCount);
            IntBuffer entityPrebuiltBLASs = stack.mallocInt(entityCount);
            IntBuffer entityPosts = stack.mallocInt(entityCount);
            IntBuffer entityLayerCounts = stack.mallocInt(entityCount);

            IntBuffer geometryTypes = stack.mallocInt(layerCount);
            LongBuffer geometryGroupNames = stack.mallocLong(layerCount);
            LongBuffer geometryContentNames = stack.mallocLong(layerCount);
            IntBuffer geometryTextures = stack.mallocInt(layerCount);
            IntBuffer vertexFormats = stack.mallocInt(layerCount);
            IntBuffer indexFormats = stack.mallocInt(layerCount);
            IntBuffer vertexCounts = stack.mallocInt(layerCount);
            LongBuffer vertices = stack.mallocLong(layerCount);

            int layerIndex = 0;
            for (int entityIndex = 0; entityIndex < entityCount; entityIndex++) {
                EntityRenderData data = entityRenderDataList.get(entityIndex);
                entityHashCodes.put(entityIndex, data.getHashCode());
                entityPosXs.put(entityIndex, data.getX());
                entityPosYs.put(entityIndex, data.getY());
                entityPosZs.put(entityIndex, data.getZ());
                entityRayTracingFlags.put(entityIndex, data.getRayTracingFlag());
                entityPostRenderFlags.put(entityIndex, data.getPostRenderFlag());
                entityPrebuiltBLASs.put(entityIndex, data.getPrebuiltBLAS());
                entityPosts.put(entityIndex, data.isPost() ? 1 : 0);
                entityLayerCounts.put(entityIndex, data.size());

                for (EntityRenderLayer layer : data) {
                    MeshData builtBuffer = layer.builtBuffer();
                    MeshData.DrawState drawState = builtBuffer.drawState();
                    ByteBuffer vertexBuffer = builtBuffer.vertexBuffer().order(ByteOrder.nativeOrder());

                    geometryTypes.put(layerIndex,
                        Constants.GeometryTypes.getGeometryType(layer.renderLayer(), layer.reflect()).getValue());
                    geometryGroupNames.put(layerIndex,
                        MemoryUtil.memAddress(stack.UTF8(layer.renderLayer().toString())));
                    geometryContentNames.put(layerIndex,
                        MemoryUtil.memAddress(stack.UTF8(layer.contentName() == null ? "" : layer.contentName())));
                    geometryTextures.put(layerIndex, -1);
                    vertexFormats.put(layerIndex,
                        Constants.DefaultVertexFormat.getValue(drawState.format()));
                    indexFormats.put(layerIndex,
                        Constants.IndexTypes.getValue(drawState.indexType()));
                    vertexCounts.put(layerIndex, drawState.vertexCount());
                    vertices.put(layerIndex, MemoryUtil.memAddress(vertexBuffer));
                    layerIndex++;
                }
            }

            queueBuild(1.0F, Constants.Coordinates.WORLD.getValue(), false, entityCount,
                MemoryUtil.memAddress(entityHashCodes), MemoryUtil.memAddress(entityPosXs),
                MemoryUtil.memAddress(entityPosYs), MemoryUtil.memAddress(entityPosZs),
                MemoryUtil.memAddress(entityRayTracingFlags),
                MemoryUtil.memAddress(entityPostRenderFlags),
                MemoryUtil.memAddress(entityPrebuiltBLASs), MemoryUtil.memAddress(entityPosts),
                MemoryUtil.memAddress(entityLayerCounts), MemoryUtil.memAddress(geometryTypes),
                MemoryUtil.memAddress(geometryGroupNames), MemoryUtil.memAddress(geometryContentNames),
                MemoryUtil.memAddress(geometryTextures), MemoryUtil.memAddress(vertexFormats),
                MemoryUtil.memAddress(indexFormats), MemoryUtil.memAddress(vertexCounts),
                MemoryUtil.memAddress(vertices));
        } finally {
            if (closeBuffers) {
                closeBuiltBuffers(entityRenderDataList);
            }
        }
    }

    private static void collectProviderLayers(Object storageVertexConsumerProvider,
        EntityRenderData data, boolean reflect) {
        if (!(storageVertexConsumerProvider instanceof StorageVertexConsumerProvider provider)) {
            return;
        }

        for (Map.Entry<RenderType, VertexConsumer> entry : provider.getLayers().entrySet()) {
            VertexConsumer consumer = entry.getValue();
            if (!(consumer instanceof PBRVertexConsumer pbrVertexConsumer)) {
                continue;
            }
            MeshData builtBuffer = pbrVertexConsumer.endNullable();
            if (builtBuffer != null) {
                data.add(new EntityRenderLayer(entry.getKey(), builtBuffer, reflect, ""));
            }
        }
    }

    private static int rayTracingFlagValue(Object rayTracingFlag) {
        return rayTracingFlag instanceof Constants.RayTracingFlags flag ? flag.getValue() : 0;
    }

    private static int postRenderFlagValue(Object postRenderFlag) {
        return postRenderFlag instanceof Constants.PostRenderFlags flag ? flag.getValue() : 0;
    }

    private static void closeBuiltBuffers(EntityRenderDataList entityRenderDataList) {
        for (EntityRenderData entityRenderData : entityRenderDataList) {
            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                entityRenderLayer.builtBuffer().close();
            }
        }
    }

    public record CrumblingRenderData(List<?> providers, EntityRenderDataList renderDataList) {
    }

    public record EntityRenderLayer(RenderType renderLayer, MeshData builtBuffer,
                                    boolean reflect, String contentName) {
    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final int hashCode;
        private final int rayTracingFlag;
        private final int postRenderFlag;
        private final int prebuiltBLAS;
        private final boolean post;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(int hashCode, double x, double y, double z, int rayTracingFlag,
            int postRenderFlag,
            int prebuiltBLAS,
            boolean post) {
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rayTracingFlag = rayTracingFlag;
            this.postRenderFlag = postRenderFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.post = post;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public int getRayTracingFlag() {
            return rayTracingFlag;
        }

        public int getPostRenderFlag() {
            return postRenderFlag;
        }

        public int getPrebuiltBLAS() {
            return prebuiltBLAS;
        }

        public int getHashCode() {
            return hashCode;
        }

        public boolean isPost() {
            return post;
        }
    }

    public static class EntityRenderDataList extends ArrayList<EntityRenderData> {

        private int totalLayersCount;

        @Override
        public boolean add(EntityRenderData entityRenderData) {
            totalLayersCount += entityRenderData.size();
            return super.add(entityRenderData);
        }

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }
}
