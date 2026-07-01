package com.radiance.client.constant;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexFormat;

public class Constants {

    public enum IndexTypes {
        SHORT(IndexType.SHORT, 0),
        INT(IndexType.INT, 1);

        private static final Map<IndexType, Integer>
            BY_INDEX_TYPE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(IndexTypes::getIndexType, IndexTypes::getValue)));

        private final IndexType indexType;
        private final int value;

        IndexTypes(IndexType indexType, int value) {
            this.indexType = indexType;
            this.value = value;
        }

        public static int getValue(IndexType indexType) {
            return BY_INDEX_TYPE.get(indexType);
        }

        public IndexType getIndexType() {
            return indexType;
        }

        public int getValue() {
            return value;
        }
    }

    public enum DrawModes {
        LINES(PrimitiveTopology.LINES, 0),
        DEBUG_LINE_STRIP(PrimitiveTopology.DEBUG_LINE_STRIP, 1),
        DEBUG_LINES(PrimitiveTopology.DEBUG_LINES, 2),
        TRIANGLES(PrimitiveTopology.TRIANGLES, 4),
        TRIANGLE_STRIP(PrimitiveTopology.TRIANGLE_STRIP, 5),
        TRIANGLE_FAN(PrimitiveTopology.TRIANGLE_FAN, 6),
        QUADS(PrimitiveTopology.QUADS, 7);

        private static final Map<PrimitiveTopology, Integer>
            BY_DRAW_MODE =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(Collectors.toMap(DrawModes::getDrawMode, DrawModes::getValue)));

        private final PrimitiveTopology drawMode;
        private final int value;

        DrawModes(PrimitiveTopology drawMode, int value) {
            this.drawMode = drawMode;
            this.value = value;
        }

        public static int getValue(PrimitiveTopology drawMode) {
            return BY_DRAW_MODE.get(drawMode);
        }

        public PrimitiveTopology getDrawMode() {
            return drawMode;
        }

        public int getValue() {
            return value;
        }
    }

    public enum DefaultVertexFormat {
        POSITION_COLOR_TEXTURE_LIGHT_NORMAL(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, 0),
        POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.ENTITY,
            1),
        POSITION_TEXTURE_COLOR_LIGHT(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, 2),
        POSITION(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION, 3),
        POSITION_COLOR(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR, 4),
        POSITION_COLOR_LINE_WIDTH(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, 5),
        POSITION_COLOR_NORMAL_LINE_WIDTH(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH, 5),
        POSITION_COLOR_LIGHT(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, 6),
        POSITION_TEXTURE(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX, 7),
        POSITION_TEXTURE_COLOR(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR, 8),
        POSITION_COLOR_TEXTURE_LIGHT(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, 9),
        POSITION_TEXTURE_LIGHT_COLOR(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR, 10),
        POSITION_TEXTURE_COLOR_NORMAL(
            com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL, 11),
        PBR_TRIANGLE(getPbrTriangleFormat(), 12);

        private static final Map<VertexFormat, Integer>
            BY_VERTEX_FORMAT =
            Collections.unmodifiableMap(Arrays.stream(values())
                .collect(
                    Collectors.toMap(
                        DefaultVertexFormat::getVertexFormat,
                        DefaultVertexFormat::getValue,
                        (oldValue, newValue) -> newValue)));

        private final VertexFormat vertexFormat;
        private final int value;

        DefaultVertexFormat(VertexFormat vertexFormat, int value) {
            this.vertexFormat = vertexFormat;
            this.value = value;
        }

        public static int getValue(VertexFormat vertexFormat) {
            return BY_VERTEX_FORMAT.get(vertexFormat);
        }

        public VertexFormat getVertexFormat() {
            return vertexFormat;
        }

        public int getValue() {
            return value;
        }

        private static VertexFormat getPbrTriangleFormat() {
            try {
                Class<?> formats = Class.forName("com.radiance.client.vertex.PBRVertexFormats");
                return (VertexFormat) formats.getField("PBR_TRIANGLE").get(null);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

    public enum GeometryTypes {
        SHADOW(0),
        WORLD_SOLID(1),
        WORLD_TRANSPARENT(2),
        WORLD_NO_REFLECT(3),
        WORLD_CLOUD(4),
        BOAT_WATER_MASK(5),
        END_PORTAL(6),
        END_GATEWAY(7);

        private final int value;

        GeometryTypes(int value) {
            this.value = value;
        }

        public static GeometryTypes getGeometryType(RenderType renderLayer, boolean reflect) {
            String layerName = renderLayer.toString();
            // single objects
            if (layerName.contains("water_mask")) {
                return BOAT_WATER_MASK;
            } else if (layerName.contains("end_portal")) {
                return END_PORTAL;
            } else if (layerName.contains("end_gateway")) {
                return END_GATEWAY;
            }

            if (layerName.contains("cloud")) {
                return WORLD_CLOUD;
            }

            if (!reflect) {
                return WORLD_NO_REFLECT;
            }

            if (layerName.contains("solid")) {
                // solid
                return WORLD_SOLID;
            }

            if (renderLayer.hasBlending() || renderLayer.sortOnUpload()) {
                return WORLD_TRANSPARENT;
            } else {
                // cut out
                return WORLD_TRANSPARENT;
            }
        }

        public int getValue() {
            return value;
        }
    }

    public enum Coordinates {
        WORLD(0),
        CAMERA(1),
        CAMERA_SHIFT(2);

        private final int value;

        Coordinates(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum RayTracingFlags {
        WORLD(0b00000001),
        PLAYER(0b00000010),
        FISHING_BOBBER(0b00000100),
        HAND(0b00001000),
        PARTICLE(0b00100000),
        CLOUD(0b01000000),
        BOAT_WATER_MASK(0b10000000);

        private final int value;

        RayTracingFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum PostRenderFlags {
        WEATHER(0b0001),
        PARTICLE(0b0010),
        TEXT(0b0100),
        NAME_TAG(0b1000);

        private final int value;

        PostRenderFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
