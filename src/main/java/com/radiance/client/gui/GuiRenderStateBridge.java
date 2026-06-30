package com.radiance.client.gui;

import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.texture.TextureTracker;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.TiledBlitRenderState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class GuiRenderStateBridge {

    private static final int VERTEX_FORMAT_POSITION_COLOR = 4;
    private static final int VERTEX_FORMAT_POSITION_TEX_COLOR = 8;
    private static final int INDEX_TYPE_SHORT = 0;
    private static final int DRAW_MODE_QUADS = 7;
    private static final int UNIFORM_SIZE = 16;
    private static final int POSITION_COLOR_STRIDE = Float.BYTES * 3 + Integer.BYTES;
    private static final int POSITION_TEX_COLOR_STRIDE = Float.BYTES * 5 + Integer.BYTES;
    private static final int MAX_SUBMITTED_ELEMENTS = 2048;

    private static volatile Integer colorShaderId;
    private static volatile Integer textureShaderId;
    private static volatile boolean disabled;
    private static volatile boolean loggedSubmission;
    private static volatile boolean loggedFailure;

    private GuiRenderStateBridge() {
    }

    public static void render(GuiRenderState renderState, int guiWidth, int guiHeight,
        int framebufferWidth, int framebufferHeight) {
        if (renderState == null || guiWidth <= 0 || guiHeight <= 0
            || framebufferWidth <= 0 || framebufferHeight <= 0
            || disabled || !RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        Stats stats = new Stats();
        try {
            ensureShadersRegistered();
            configureOverlayState(framebufferWidth, framebufferHeight);
            renderState.forEachElement(element -> submitElement(element, guiWidth, guiHeight,
                    framebufferWidth, framebufferHeight, stats),
                GuiRenderState.TraverseRange.ALL);
            renderState.forEachText(ignored -> stats.textStates++);
            renderState.forEachItem(ignored -> stats.itemStates++);
            PipelineStateProxy.ViewportState.setScissorEnabled(false);
            logSubmission(stats);
        } catch (Throwable throwable) {
            disabled = true;
            if (!loggedFailure) {
                loggedFailure = true;
                RadianceClient.LOGGER.error(
                    "Radiance GUI bridge disabled after failed 26.2 GuiRenderState submission",
                    throwable);
            }
        }
    }

    private static void ensureShadersRegistered() throws IOException {
        if (colorShaderId != null && textureShaderId != null) {
            return;
        }
        if (RadianceClient.radianceDir == null) {
            throw new IllegalStateException("Radiance directory is not initialized");
        }

        Path shaderDir = RadianceClient.radianceDir.resolve("temp").resolve("gui-overlay");
        Files.createDirectories(shaderDir);
        Path colorVert = shaderDir.resolve("gui_position_color.vert");
        Path colorFrag = shaderDir.resolve("gui_position_color.frag");
        Path textureVert = shaderDir.resolve("gui_position_tex_color.vert");
        Path textureFrag = shaderDir.resolve("gui_position_tex_color.frag");
        writeIfChanged(colorVert, COLOR_VERTEX_SHADER);
        writeIfChanged(colorFrag, COLOR_FRAGMENT_SHADER);
        writeIfChanged(textureVert, TEXTURE_VERTEX_SHADER);
        writeIfChanged(textureFrag, TEXTURE_FRAGMENT_SHADER);

        if (colorShaderId == null) {
            colorShaderId = ShaderProxy.registerShader("radiance_gui_color_26_2",
                VERTEX_FORMAT_POSITION_COLOR, DRAW_MODE_QUADS, UNIFORM_SIZE,
                colorVert.toString(), colorFrag.toString(), new String[0], new String[0]);
        }
        if (textureShaderId == null) {
            textureShaderId = ShaderProxy.registerShader("radiance_gui_texture_26_2",
                VERTEX_FORMAT_POSITION_TEX_COLOR, DRAW_MODE_QUADS, UNIFORM_SIZE,
                textureVert.toString(), textureFrag.toString(), new String[0], new String[0]);
        }
    }

    private static void configureOverlayState(int framebufferWidth, int framebufferHeight) {
        PipelineStateProxy.ViewportState.setViewport(0, 0, framebufferWidth, framebufferHeight);
        PipelineStateProxy.ViewportState.setScissorEnabled(false);
        PipelineStateProxy.ColorBlendState.setBlendEnable(true);
        PipelineStateProxy.ColorBlendState.glSetBlendFuncSeparate(GL11.GL_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        PipelineStateProxy.ColorBlendState.glSetBlendOpCombined(GL14.GL_FUNC_ADD);
        PipelineStateProxy.ColorBlendState.glSetColorWriteMask(true, true, true, true);
        PipelineStateProxy.DepthStencilState.setDepthTestEnable(false);
        PipelineStateProxy.DepthStencilState.setDepthWriteEnable(false);
    }

    private static void submitElement(GuiElementRenderState element, int guiWidth,
        int guiHeight, int framebufferWidth, int framebufferHeight, Stats stats) {
        if (stats.submitted >= MAX_SUBMITTED_ELEMENTS) {
            stats.skipped++;
            return;
        }

        boolean hasTexture = hasTexture(element.textureSetup());
        int textureId = hasTexture ? textureId(element.textureSetup()) : 0;
        if (hasTexture && textureId == 0) {
            stats.skippedTextures++;
            return;
        }

        GuiVertexCollector collector = new GuiVertexCollector();
        element.buildVertices(collector);
        int vertexCount = collector.vertexCount();
        if (vertexCount == 0 || vertexCount % 4 != 0) {
            stats.skipped++;
            return;
        }
        if (isFullscreenTexturedEffect(element, collector, guiWidth, guiHeight)) {
            stats.fullscreenEffectsSkipped++;
            return;
        }

        applyScissor(element.scissorArea(), guiWidth, guiHeight, framebufferWidth,
            framebufferHeight);
        if (textureId != 0 && collector.hasUv()) {
            submitCollectedTextured(collector, textureId, guiWidth, guiHeight);
        } else {
            submitCollectedColor(collector, guiWidth, guiHeight);
        }

        if (element instanceof ColoredRectangleRenderState) {
            stats.rectangles++;
        } else if (element instanceof BlitRenderState || element instanceof TiledBlitRenderState) {
            stats.blits++;
        } else if (element instanceof GlyphRenderState) {
            stats.glyphs++;
        } else {
            stats.generic++;
        }
        stats.submitted++;
    }

    private static void submitCollectedColor(GuiVertexCollector collector, int guiWidth,
        int guiHeight) {
        int vertexCount = collector.vertexCount();
        ByteBuffer vertices = MemoryUtil.memAlloc(vertexCount * POSITION_COLOR_STRIDE);
        try (MemoryStack stack = stackPush()) {
            vertices.order(ByteOrder.nativeOrder());
            for (int i = 0; i < vertexCount; i++) {
                GuiVertex vertex = collector.vertex(i);
                putPositionColorVertex(vertices, vertex.x, vertex.y, vertex.color, guiWidth,
                    guiHeight);
            }
            vertices.flip();
            draw(vertices, POSITION_COLOR_STRIDE, colorShaderId, 0, vertexCount,
                vertexCount / 4 * 6, stack);
        } finally {
            MemoryUtil.memFree(vertices);
        }
    }

    private static void submitCollectedTextured(GuiVertexCollector collector, int textureId,
        int guiWidth, int guiHeight) {
        int vertexCount = collector.vertexCount();
        ByteBuffer vertices = MemoryUtil.memAlloc(vertexCount * POSITION_TEX_COLOR_STRIDE);
        try (MemoryStack stack = stackPush()) {
            vertices.order(ByteOrder.nativeOrder());
            for (int i = 0; i < vertexCount; i++) {
                GuiVertex vertex = collector.vertex(i);
                putPositionTexColorVertex(vertices, vertex.x, vertex.y, vertex.u, vertex.v,
                    vertex.color, guiWidth, guiHeight);
            }
            vertices.flip();
            draw(vertices, POSITION_TEX_COLOR_STRIDE, textureShaderId, textureId, vertexCount,
                vertexCount / 4 * 6, stack);
        } finally {
            MemoryUtil.memFree(vertices);
        }
    }

    private static void draw(ByteBuffer vertices, int stride, int shaderId, int textureId,
        int vertexCount, int indexCount, MemoryStack stack) {
        int vertexId = BufferProxy.allocateBuffer();
        BufferProxy.initializeBuffer(vertexId, vertexCount * stride,
            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        BufferProxy.queueUpload(MemoryUtil.memAddress(vertices), vertexId);

        int indexId = BufferProxy.allocateBuffer();
        BufferProxy.initializeBuffer(indexId, Short.BYTES * indexCount,
            VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        BufferProxy.buildIndexBuffer(indexId, INDEX_TYPE_SHORT, DRAW_MODE_QUADS,
            vertexCount, indexCount);

        ByteBuffer uniform = stack.calloc(UNIFORM_SIZE);
        uniform.putInt(0, textureId);
        ShaderProxy.draw(vertexId, indexId, shaderId, indexCount, INDEX_TYPE_SHORT,
            MemoryUtil.memAddress(uniform), UNIFORM_SIZE);
    }

    private static void putPositionColorVertex(ByteBuffer buffer, float x, float y, int color,
        int guiWidth, int guiHeight) {
        buffer.putFloat(toNdcX(x, guiWidth));
        buffer.putFloat(toNdcY(y, guiHeight));
        buffer.putFloat(0.0f);
        buffer.putInt(packArgbForRgbaUnorm(color));
    }

    private static void putPositionTexColorVertex(ByteBuffer buffer, float x, float y, float u,
        float v, int color, int guiWidth, int guiHeight) {
        buffer.putFloat(toNdcX(x, guiWidth));
        buffer.putFloat(toNdcY(y, guiHeight));
        buffer.putFloat(0.0f);
        buffer.putFloat(u);
        buffer.putFloat(v);
        buffer.putInt(packArgbForRgbaUnorm(color));
    }

    private static float toNdcX(float x, int guiWidth) {
        return x / guiWidth * 2.0f - 1.0f;
    }

    private static float toNdcY(float y, int guiHeight) {
        return y / guiHeight * 2.0f - 1.0f;
    }

    private static int textureId(TextureSetup textureSetup) {
        if (textureSetup == null) {
            return 0;
        }
        GpuTextureView view = textureSetup.texure0();
        if (view == null || view.isClosed()) {
            return 0;
        }
        GpuTexture texture = view.texture();
        if (texture == null || texture.isClosed()) {
            return 0;
        }
        return TextureTracker.getOrRegisterGuiTexture(texture);
    }

    private static boolean hasTexture(TextureSetup textureSetup) {
        return textureSetup != null
            && textureSetup.texure0() != null
            && !textureSetup.texure0().isClosed();
    }

    private static void applyScissor(ScreenRectangle scissor, int guiWidth, int guiHeight,
        int framebufferWidth, int framebufferHeight) {
        if (scissor == null || scissor.width() <= 0 || scissor.height() <= 0) {
            PipelineStateProxy.ViewportState.setScissorEnabled(false);
            return;
        }

        float scaleX = framebufferWidth / (float) guiWidth;
        float scaleY = framebufferHeight / (float) guiHeight;
        int x = Math.max(0, Math.round(scissor.left() * scaleX));
        int y = Math.max(0, Math.round(scissor.top() * scaleY));
        int width = Math.max(0,
            Math.min(framebufferWidth - x, Math.round(scissor.width() * scaleX)));
        int height = Math.max(0,
            Math.min(framebufferHeight - y, Math.round(scissor.height() * scaleY)));
        PipelineStateProxy.ViewportState.setScissorEnabled(width > 0 && height > 0);
        if (width > 0 && height > 0) {
            PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
        }
    }

    private static int packArgbForRgbaUnorm(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        return red | (green << 8) | (blue << 16) | (alpha << 24);
    }

    private static void writeIfChanged(Path path, String source) throws IOException {
        if (Files.exists(path) && Files.readString(path, StandardCharsets.UTF_8).equals(source)) {
            return;
        }
        Files.writeString(path, source, StandardCharsets.UTF_8);
    }

    private static void logSubmission(Stats stats) {
        if (loggedSubmission || stats.submitted == 0) {
            return;
        }
        loggedSubmission = true;
        RadianceClient.LOGGER.info(
            "Radiance GUI bridge: submitted {} 26.2 GuiRenderState overlay elements (rectangles={}, blits={}, glyphs={}, generic={}, skipped={}, skippedTextures={}, skippedFullscreenEffects={}, textStates={}, itemStates={})",
            stats.submitted, stats.rectangles, stats.blits, stats.glyphs, stats.generic,
            stats.skipped, stats.skippedTextures, stats.fullscreenEffectsSkipped,
            stats.textStates, stats.itemStates);
    }

    private static boolean isFullscreenTexturedEffect(GuiElementRenderState element,
        GuiVertexCollector collector, int guiWidth, int guiHeight) {
        if (!(element instanceof BlitRenderState || element instanceof TiledBlitRenderState)
            || !collector.hasUv()) {
            return false;
        }

        float coveredWidth = collector.maxX() - collector.minX();
        float coveredHeight = collector.maxY() - collector.minY();
        return collector.minX() <= guiWidth * 0.02f
            && collector.minY() <= guiHeight * 0.02f
            && collector.maxX() >= guiWidth * 0.98f
            && collector.maxY() >= guiHeight * 0.98f
            && coveredWidth >= guiWidth * 0.95f
            && coveredHeight >= guiHeight * 0.95f;
    }

    private static final class Stats {

        private int submitted;
        private int rectangles;
        private int blits;
        private int glyphs;
        private int generic;
        private int skipped;
        private int skippedTextures;
        private int fullscreenEffectsSkipped;
        private int textStates;
        private int itemStates;
    }

    private static final class GuiVertexCollector implements VertexConsumer {

        private GuiVertex[] vertices = new GuiVertex[32];
        private int count;
        private GuiVertex current;
        private boolean hasUv;
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            ensureCapacity(count + 1);
            current = new GuiVertex(x, y, 0.0f, 0.0f, 0xFFFFFFFF);
            vertices[count++] = current;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            if (current != null) {
                current.color = ((alpha & 0xFF) << 24)
                    | ((red & 0xFF) << 16)
                    | ((green & 0xFF) << 8)
                    | (blue & 0xFF);
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (current != null) {
                current.color = color;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (current != null) {
                current.u = u;
                current.v = v;
                hasUv = true;
            }
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
        public VertexConsumer setLineWidth(float lineWidth) {
            return this;
        }

        private int vertexCount() {
            return count;
        }

        private boolean hasUv() {
            return hasUv;
        }

        private GuiVertex vertex(int index) {
            return vertices[index];
        }

        private float minX() {
            return minX;
        }

        private float minY() {
            return minY;
        }

        private float maxX() {
            return maxX;
        }

        private float maxY() {
            return maxY;
        }

        private void ensureCapacity(int size) {
            if (size <= vertices.length) {
                return;
            }
            GuiVertex[] newVertices = new GuiVertex[Math.max(size, vertices.length * 2)];
            System.arraycopy(vertices, 0, newVertices, 0, vertices.length);
            vertices = newVertices;
        }
    }

    private static final class GuiVertex {

        private final float x;
        private final float y;
        private float u;
        private float v;
        private int color;

        private GuiVertex(float x, float y, float u, float v, int color) {
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
            this.color = color;
        }
    }

    private static final String COLOR_VERTEX_SHADER = """
        #version 460

        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec4 inColor;

        layout(location = 0) out vec4 fragColor;

        void main() {
            gl_Position = vec4(inPosition, 1.0);
            fragColor = inColor;
        }
        """;

    private static final String COLOR_FRAGMENT_SHADER = """
        #version 460

        layout(location = 0) in vec4 fragColor;
        layout(location = 0) out vec4 outColor;

        void main() {
            outColor = fragColor;
        }
        """;

    private static final String TEXTURE_VERTEX_SHADER = """
        #version 460

        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec2 inUv;
        layout(location = 2) in vec4 inColor;

        layout(location = 0) out vec2 fragUv;
        layout(location = 1) out vec4 fragColor;

        void main() {
            gl_Position = vec4(inPosition, 1.0);
            fragUv = inUv;
            fragColor = inColor;
        }
        """;

    private static final String TEXTURE_FRAGMENT_SHADER = """
        #version 460
        #extension GL_EXT_nonuniform_qualifier : enable

        layout(set = 0, binding = 0) uniform sampler2D OverlayTextures[4096];
        layout(std140, set = 1, binding = 0) uniform OverlayDrawUniform {
            int textureId;
        };

        layout(location = 0) in vec2 fragUv;
        layout(location = 1) in vec4 fragColor;
        layout(location = 0) out vec4 outColor;

        void main() {
            outColor = texture(OverlayTextures[nonuniformEXT(textureId)], fragUv) * fragColor;
        }
        """;
}
