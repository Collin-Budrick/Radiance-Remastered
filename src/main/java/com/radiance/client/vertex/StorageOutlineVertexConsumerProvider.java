package com.radiance.client.vertex;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class StorageOutlineVertexConsumerProvider {

    private final StorageVertexConsumerProvider parent;
    private int red = 255;
    private int green = 255;
    private int blue = 255;
    private int alpha = 255;

    public StorageOutlineVertexConsumerProvider(StorageVertexConsumerProvider parent) {
        this.parent = parent;
    }

    public VertexConsumer getBuffer(RenderType renderLayer) {
        if (renderLayer.isOutline()) {
            VertexConsumer vertexConsumer = this.parent.getBuffer(renderLayer);
            return new OutlineVertexConsumer(vertexConsumer, this.red, this.green, this.blue,
                this.alpha);
        } else {
            VertexConsumer vertexConsumer = this.parent.getBuffer(renderLayer);
            Optional<RenderType> optional = renderLayer.outline();
            if (optional.isPresent()) {
                VertexConsumer vertexConsumer2 = this.parent.getBuffer(
                    optional.get());
                OutlineVertexConsumer
                    outlineVertexConsumer =
                    new OutlineVertexConsumer(vertexConsumer2, this.red, this.green, this.blue,
                        this.alpha);
                return new DualVertexConsumer(outlineVertexConsumer, vertexConsumer);
            } else {
                return vertexConsumer;
            }
        }
    }

    public void setColor(int red, int green, int blue, int alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Environment(EnvType.CLIENT)
    record OutlineVertexConsumer(VertexConsumer delegate, int color) implements VertexConsumer {

        public OutlineVertexConsumer(VertexConsumer delegate, int red, int green, int blue,
            int alpha) {
            this(delegate, ARGB.color(alpha, red, green, blue));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z)
                .setColor(this.color);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    @Environment(EnvType.CLIENT)
    record DualVertexConsumer(VertexConsumer first, VertexConsumer second) implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.first.addVertex(x, y, z);
            this.second.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.first.setColor(red, green, blue, alpha);
            this.second.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            this.first.setColor(color);
            this.second.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.first.setUv(u, v);
            this.second.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.first.setUv1(u, v);
            this.second.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.first.setUv2(u, v);
            this.second.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.first.setNormal(x, y, z);
            this.second.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            this.first.setLineWidth(width);
            this.second.setLineWidth(width);
            return this;
        }
    }
}
