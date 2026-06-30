package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.radiance.client.texture.TextureTracker;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.util.Map;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radiance.client.vertex.PBRVertexFormatElements.PBRAttribute;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class PBRVertexConsumer implements VertexConsumer {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int ALPHA_MODE_OPAQUE = 0;
    private static final int ALPHA_MODE_CUTOUT = 1;
    private static final int ALPHA_MODE_TRANSPARENT = 2;
    private static final int POST_TEXT_MODE_BACKGROUND = 1;
    private static final int POST_TEXT_MODE_INTENSITY = 2;
    private static final int POST_TEXT_MODE_RGBA = 3;
    private static final int POST_TEXT_MODE_BACKGROUND_SEE_THROUGH = 4;
    private static final int POST_TEXT_MODE_INTENSITY_SEE_THROUGH = 5;
    private static final int POST_TEXT_MODE_RGBA_SEE_THROUGH = 6;
    private static final int POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET = 7;
    private static final int POST_TEXT_MODE_RGBA_POLYGON_OFFSET = 8;

    private final ByteBufferBuilder allocator;
    private final VertexFormat format;
    private final PrimitiveTopology drawMode;

    private final int vertexSizeByte;
    private final int writableMask;
    private final int requiredMask;
    private final float albedoEmission = 0;
    private long vertexPointer = -1L;
    private int vertexCount = 0;
    private int currentMask = 0;
    private boolean building = true;
    private int textureID;
    private final int alphaMode;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;

    public PBRVertexConsumer(ByteBufferBuilder allocator, RenderType renderLayer) {
        this(allocator, PrimitiveTopology.QUADS, PBRVertexFormats.PBR_TRIANGLE, renderLayer);
    }

    public PBRVertexConsumer(ByteBufferBuilder allocator, ChunkSectionLayer layer) {
        this(allocator, PrimitiveTopology.QUADS, PBRVertexFormats.PBR_TRIANGLE,
            getAlphaMode(layer));
        this.textureID = getTextureId(TextureAtlas.LOCATION_BLOCKS);
    }

    private PBRVertexConsumer(ByteBufferBuilder allocator, PrimitiveTopology drawMode,
        VertexFormat format, RenderType renderLayer) {
        this(allocator, drawMode, format, getAlphaMode(renderLayer));
        this.textureID = getRenderTypeTextureId(renderLayer);
    }

    private PBRVertexConsumer(ByteBufferBuilder allocator, PrimitiveTopology drawMode,
        VertexFormat format, int alphaMode) {
        this.allocator = allocator;
        this.drawMode = drawMode;
        this.format = format;

        this.vertexSizeByte = format.getVertexSize();
        this.writableMask = allWritableMask();
        this.requiredMask = 0;

        if (this.vertexSizeByte != 128) {
            throw new IllegalStateException(
                "PBR vertex stride must be 128, got " + this.vertexSizeByte);
        }
        if (!format.contains(PBR_POS.name())) {
            throw new IllegalArgumentException("PBR format must contain POSITION element");
        }

        this.alphaMode = alphaMode;
    }

    private static int allWritableMask() {
        return bit(PBR_USE_NORM)
            | bit(PBR_NORM)
            | bit(PBR_USE_COLOR_LAYER)
            | bit(PBR_COLOR_LAYER)
            | bit(PBR_USE_TEXTURE)
            | bit(PBR_USE_OVERLAY)
            | bit(PBR_TEXTURE_UV)
            | bit(PBR_OVERLAY_UV)
            | bit(PBR_USE_GLINT)
            | bit(PBR_TEXTURE_ID)
            | bit(PBR_GLINT_UV)
            | bit(PBR_GLINT_TEXTURE)
            | bit(PBR_USE_LIGHT)
            | bit(PBR_LIGHT_UV)
            | bit(PBR_ALBEDO_EMISSION)
            | bit(PBR_POST_BASE);
    }

    private static int bit(PBRAttribute element) {
        return 1 << element.bit();
    }

    private static void putInt(long ptr, int v) {
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(ptr, v);
        } else {
            MemoryUtil.memPutShort(ptr, (short) (v & 0xFFFF));
            MemoryUtil.memPutShort(ptr + 2L, (short) ((v >>> 16) & 0xFFFF));
        }
    }

    private static int getAlphaMode(RenderType renderLayer) {
        String layerName = renderLayer.toString();
        int postTextMode = getPostTextMode(layerName);
        if (postTextMode != ALPHA_MODE_OPAQUE) {
            return postTextMode;
        }

        if (layerName.contains("solid")) {
            return ALPHA_MODE_OPAQUE;
        }

        if (layerName.contains("cutout") || !renderLayer.hasBlending()) {
            return ALPHA_MODE_CUTOUT;
        }

        return ALPHA_MODE_TRANSPARENT;
    }

    private static int getAlphaMode(ChunkSectionLayer layer) {
        if (layer == ChunkSectionLayer.TRANSLUCENT || layer.translucent()) {
            return ALPHA_MODE_TRANSPARENT;
        }

        String layerName = layer.label();
        if (layerName.contains("cutout")) {
            return ALPHA_MODE_CUTOUT;
        }

        return ALPHA_MODE_OPAQUE;
    }

    private static int getPostTextMode(String layerName) {
        return switch (layerName) {
            case "text_background" -> POST_TEXT_MODE_BACKGROUND;
            case "text_intensity" -> POST_TEXT_MODE_INTENSITY;
            case "text" -> POST_TEXT_MODE_RGBA;
            case "text_background_see_through" -> POST_TEXT_MODE_BACKGROUND_SEE_THROUGH;
            case "text_intensity_see_through" -> POST_TEXT_MODE_INTENSITY_SEE_THROUGH;
            case "text_see_through" -> POST_TEXT_MODE_RGBA_SEE_THROUGH;
            case "text_intensity_polygon_offset" -> POST_TEXT_MODE_INTENSITY_POLYGON_OFFSET;
            case "text_polygon_offset" -> POST_TEXT_MODE_RGBA_POLYGON_OFFSET;
            default -> ALPHA_MODE_OPAQUE;
        };
    }

    private static int getRenderTypeTextureId(RenderType renderLayer) {
        Identifier texture = getRenderTypeTexture(renderLayer);
        return texture == null ? 0 : getTextureId(texture);
    }

    private static Identifier getRenderTypeTexture(RenderType renderLayer) {
        try {
            Field stateField = RenderType.class.getDeclaredField("state");
            stateField.setAccessible(true);
            Object state = stateField.get(renderLayer);
            Field texturesField = state.getClass().getDeclaredField("textures");
            texturesField.setAccessible(true);
            Map<?, ?> textures = (Map<?, ?>) texturesField.get(state);
            if (textures == null || textures.isEmpty()) {
                return null;
            }

            Object binding = textures.get("Sampler0");
            if (binding == null) {
                binding = textures.values().iterator().next();
            }
            Method locationMethod = binding.getClass().getDeclaredMethod("location");
            locationMethod.setAccessible(true);
            return (Identifier) locationMethod.invoke(binding);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int getTextureId(Identifier identifier) {
        if (identifier == null || Minecraft.getInstance() == null) {
            return 0;
        }

        Integer tracked = TextureTracker.textureID2GLID.get(identifier);
        if (tracked != null && tracked != 0) {
            return tracked;
        }

        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager()
                .getTexture(identifier);
            if (texture == null || texture.getTexture() == null) {
                return 0;
            }
            return TextureTracker.getOrRegisterGpuTexture(texture.getTexture());
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    public VertexFormat getFormat() {
        return this.format;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public void setBase(float x, float y, float z) {
        this.baseX = x;
        this.baseY = y;
        this.baseZ = z;
    }

    private void ensureBuilding() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    public MeshData endNullable() {
        ensureBuilding();
        endVertex();
        MeshData built = build();
        building = false;
        vertexPointer = -1L;
        return built;
    }

    public MeshData end() {
        MeshData built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBRBufferBuilder was empty");
        }
        return built;
    }

    @Nullable
    private MeshData build() {
        if (vertexCount == 0) {
            return null;
        }

        ByteBufferBuilder.Result buf = allocator.build();
        if (buf == null) {
            return null;
        }

        int indexCount = drawMode.indexCount(vertexCount);
        IndexType indexType = IndexType.least(vertexCount);
        return new MeshData(buf,
            new MeshData.DrawState(format, vertexCount, indexCount, drawMode, indexType));
    }

    private long beginVertex() {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.reserve(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        if (this.textureID != 0) {
            int off = PBR_TEXTURE_ID.offset();
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offBase = PBR_POST_BASE.offset();
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
            // Reuse the trailing padding word after postBase for alpha mode.
            putInt(ptr + offBase + 12L, this.alphaMode);
        }

        return ptr;
    }

    private long beginVertex(int glintTextureID) {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.reserve(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        if (this.textureID != 0) {
            int off = PBR_TEXTURE_ID.offset();
            if (off >= 0) {
                putInt(ptr + off, this.textureID);
            }
        }

        int offBase = PBR_POST_BASE.offset();
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
            // Reuse the trailing padding word after postBase for alpha mode.
            putInt(ptr + offBase + 12L, this.alphaMode);
        }

        if (glintTextureID != 0) {
            int off = PBR_GLINT_TEXTURE.offset();
            if (off >= 0) {
                putInt(ptr + off, glintTextureID);
            }
        }

        return ptr;
    }

    private long beginElement(PBRAttribute element) {
        int mask = currentMask;
        int bit = bit(element);
        if ((mask & bit) == 0) {
            return -1L;
        }

        currentMask = mask & ~bit;

        long base = vertexPointer;
        if (base == -1L) {
            throw new IllegalStateException("Not currently building vertex");
        }

        int off = element.offset();
        if (off < 0) {
            throw new IllegalStateException(
                "GuiEventListener present in mask but not in format: " + element);
        }
        return base + off;
    }

    private void endVertex() {
        if (vertexCount == 0) {
            return;
        }

        int missing = currentMask & requiredMask;
        if (missing != 0) {
            throw new IllegalStateException("Missing elements in vertex mask: " + missing);
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        long base = beginVertex();
        currentMask = writableMask;

        int posOff = PBR_POS.offset();
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        return this;
    }

    public VertexConsumer addVertex(float x, float y, float z, int glintTextureID) {
        long base = beginVertex(glintTextureID);
        currentMask = writableMask;

        int posOff = PBR_POS.offset();
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        long f = beginElement(PBR_USE_COLOR_LAYER);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_COLOR_LAYER);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, red / 255.0f);
            MemoryUtil.memPutFloat(p + 4L, green / 255.0f);
            MemoryUtil.memPutFloat(p + 8L, blue / 255.0f);
            MemoryUtil.memPutFloat(p + 12L, alpha / 255.0f);
        }
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        return this.setColor(ARGB.red(color), ARGB.green(color), ARGB.blue(color),
            ARGB.alpha(color));
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        long f = beginElement(PBR_USE_TEXTURE);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_TEXTURE_UV);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        long f = beginElement(PBR_USE_OVERLAY);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_OVERLAY_UV);
        if (p != -1L) {
            putInt(p, u);
            putInt(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        long f = beginElement(PBR_USE_LIGHT);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_LIGHT_UV);
        if (p != -1L) {
            putInt(p, u);
            putInt(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        long f = beginElement(PBR_USE_NORM);
        if (f != -1L) {
            putInt(f, 1);
        }

        long p = beginElement(PBR_NORM);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }
        return this;
    }

    public VertexConsumer albedoEmission(float emission) {
        long p = beginElement(PBR_ALBEDO_EMISSION);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, emission);
        }
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    public static class GLint implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private int glintTextureID;

        public GLint(PBRVertexConsumer delegate, RenderType glintRenderLayer) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);

            long f = delegate.beginElement(PBR_USE_GLINT);
            if (f != -1L) {
                putInt(f, 1);
            }

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, u);
                MemoryUtil.memPutFloat(p + 4L, v);
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
    }

    public static class GLintOverlay implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final Matrix4f inverseTextureMatrix;
        private final Matrix3f inverseNormalMatrix;
        private final float textureScale;
        private final Vector3f normal = new Vector3f();
        private final Vector3f pos = new Vector3f();
        private int glintTextureID;
        private float x;
        private float y;
        private float z;

        public GLintOverlay(PBRVertexConsumer delegate, RenderType glintRenderLayer,
            PoseStack.Pose matrix, float textureScale) {
            this.delegate = delegate;
            this.inverseTextureMatrix = new Matrix4f(matrix.pose()).invert();
            this.inverseNormalMatrix = new Matrix3f(matrix.normal()).invert();
            this.textureScale = textureScale;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.addVertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            Vector3f vector3f = this.inverseNormalMatrix.transform(x, y, z, this.pos);
            Direction direction = Direction.getApproximateNearest(vector3f.x(), vector3f.y(),
                vector3f.z());
            Vector3f vector3f2 = this.inverseTextureMatrix.transformPosition(this.x, this.y, this.z,
                this.normal);
            vector3f2.rotateY((float) Math.PI);
            vector3f2.rotateX((float) (-Math.PI / 2));
            vector3f2.rotate(direction.getRotation());

            long f = delegate.beginElement(PBR_USE_GLINT);
            if (f != -1L) {
                putInt(f, 1);
            }

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, -vector3f2.x() * this.textureScale);
                MemoryUtil.memPutFloat(p + 4L, -vector3f2.y() * this.textureScale);
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
    }
}
