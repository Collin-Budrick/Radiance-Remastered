package com.radiance.client.vertex;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

@Environment(EnvType.CLIENT)
public class StorageVertexConsumerProvider {

    protected final Map<RenderType, VertexConsumer> pending = new HashMap<>();
    protected final Map<RenderType, ByteBufferBuilder> allocated = new HashMap<>();

    private int size = 0;

    public StorageVertexConsumerProvider(int size) {
        this.size = size;
    }

    public VertexConsumer getBuffer(RenderType renderLayer) {
        VertexConsumer vertexConsumer = this.pending.get(renderLayer);

        if (vertexConsumer == null) {
            ByteBufferBuilder bufferAllocator = new ByteBufferBuilder(size);
            allocated.put(renderLayer, bufferAllocator);

            PrimitiveTopology drawMode = renderLayer.primitiveTopology();
            VertexFormat vertexFormat = renderLayer.format();

            if (drawMode == PrimitiveTopology.QUADS) {
                vertexConsumer = new PBRVertexConsumer(bufferAllocator, renderLayer);
            } else {
                vertexConsumer = new BufferBuilder(bufferAllocator, drawMode, vertexFormat);
            }
            this.pending.put(renderLayer, vertexConsumer);
        }
        return vertexConsumer;
    }

    public Map<RenderType, VertexConsumer> getLayers() {
        return this.pending;
    }

    public void close() {
        for (Map.Entry<RenderType, ByteBufferBuilder> entry : this.allocated.entrySet()) {
            entry.getValue()
                .close();
        }
        this.pending.clear();
    }
}
