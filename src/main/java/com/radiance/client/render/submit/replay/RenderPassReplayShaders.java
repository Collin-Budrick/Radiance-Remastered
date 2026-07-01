package com.radiance.client.render.submit.replay;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

final class RenderPassReplayShaders {

    private static final int VERTEX_FORMAT_POSITION_COLOR = 4;
    private static final int VERTEX_FORMAT_ENTITY = 1;
    private static final int VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH =
        DrawCommandProxy.RenderPass.VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH;
    private static final int VERTEX_FORMAT_POSITION_TEX_COLOR = 8;
    private static final int TEXTURE_UNIFORM_SIZE = 16;
    private static final int MAT4_BYTES = 64;
    private static final int DYNAMIC_TRANSFORMS_BYTES = 160;
    private static final int GLOBALS_BYTES = 64;
    private static final int ENTITY_UNIFORM_SIZE = 240;
    private static final int LINE_UNIFORM_SIZE = MAT4_BYTES + DYNAMIC_TRANSFORMS_BYTES
        + GLOBALS_BYTES;
    private static final Identifier LINES_PIPELINE =
        Identifier.fromNamespaceAndPath("minecraft", "pipeline/lines");
    private static final Identifier ENTITY_CUTOUT_PIPELINE =
        Identifier.fromNamespaceAndPath("minecraft", "pipeline/entity_cutout");
    private static final Identifier ENTITY_CUTOUT_CULL_PIPELINE =
        Identifier.fromNamespaceAndPath("minecraft", "pipeline/entity_cutout_cull");
    private static final Identifier ARMOR_CUTOUT_NO_CULL_PIPELINE =
        Identifier.fromNamespaceAndPath("minecraft", "pipeline/armor_cutout_no_cull");
    private static final Identifier ITEM_CUTOUT_PIPELINE =
        Identifier.fromNamespaceAndPath("minecraft", "pipeline/item_cutout");

    private static final Map<RenderPipeline, UniformPayload> UNIFORM_PAYLOADS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean LOGGED_SHADER = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    private RenderPassReplayShaders() {
    }

    static void preparePipelineShader(RenderPipeline pipeline, List<VertexFormat> vertexFormats) {
        if (!RendererAvailability.isRendererLifecycleActive() || pipeline == null
            || vertexFormats == null || vertexFormats.isEmpty()) {
            return;
        }
        if (vertexFormats.size() != 1) {
            return;
        }

        int vertexFormatType = DrawCommandProxy.RenderPass.vertexFormatType(vertexFormats.getFirst());
        if (vertexFormatType == VERTEX_FORMAT_ENTITY
            && !isSupportedEntityReplayPipeline(pipeline)) {
            return;
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH
            && !isSupportedLineReplayPipeline(pipeline)) {
            return;
        }
        if (vertexFormatType != VERTEX_FORMAT_POSITION_COLOR
            && vertexFormatType != VERTEX_FORMAT_ENTITY
            && vertexFormatType != VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH
            && vertexFormatType != VERTEX_FORMAT_POSITION_TEX_COLOR) {
            return;
        }
        if (RenderPassNativeResourceMirror.capturePipeline(pipeline).hasShaderId()) {
            return;
        }

        int drawMode = DrawCommandProxy.RenderPass.drawModeValue(pipeline.getPrimitiveTopology());
        if (!isSupportedDrawMode(drawMode, vertexFormatType)) {
            return;
        }

        int uniformSize = uniformSize(vertexFormatType);
        try {
            ShaderFiles files = ensureShaderFiles(vertexFormatType);
            int shaderId = ShaderProxy.registerShader(shaderKey(pipeline, vertexFormatType,
                    drawMode, uniformSize), vertexFormatType, drawMode, uniformSize,
                files.vertex().toString(), files.fragment().toString(), new String[0],
                new String[0]);
            if (shaderId < 0) {
                return;
            }
            RenderPassNativeResourceMirror.rememberPipelineShader(pipeline, shaderId,
                "generic-replay-" + displayVertexFormat(vertexFormatType));
            if (LOGGED_SHADER.compareAndSet(false, true)) {
                RadianceClient.LOGGER.info(
                    "Radiance render-pass replay shader bridge: registered generic 26.2 shader id={} pipeline={} vertexFormat={} drawMode={} uniformSize={}",
                    shaderId, pipeline.getLocation(), vertexFormatType, drawMode, uniformSize);
            }
        } catch (IOException | RuntimeException | LinkageError exception) {
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                RadianceClient.LOGGER.warn(
                    "Radiance render-pass replay shader bridge: generic shader registration failed; packets will fall back",
                    exception);
            }
        }
    }

    static RenderPassDrawPacket.UniformBinding simpleUniform(RenderPipeline pipeline,
        RenderPassDrawPacket.PipelineBinding pipelineBinding,
        List<RenderPassDrawPacket.TextureBinding> textures,
        RenderPassDrawPacket.BufferSliceBinding dynamicTransforms,
        List<RenderPassDrawPacket.UniformBinding> uniforms) {
        if (!RendererAvailability.isRendererLifecycleActive() || pipeline == null
            || pipelineBinding == null || pipelineBinding.nativeReference() == null
            || !pipelineBinding.nativeReference().hasShaderId()
            || !isReplayShader(pipelineBinding)) {
            return null;
        }

        int vertexFormatType = vertexFormatType(pipelineBinding);
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR) {
            UniformPayload payload = payloadFor(pipeline, 0);
            return new RenderPassDrawPacket.UniformBinding("radianceSimpleColor",
                RenderPassDrawPacket.UniformKind.BUFFER, null, null,
                RenderPassNativeResourceMirror.rememberUniform(payload,
                    "radianceSimpleColor", 0L, 0, "generic-replay-color-uniform"), null,
                true);
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
            if (!isSupportedLineReplayPipeline(pipelineBinding)) {
                return null;
            }
            byte[] projection = copyProjectionMatrix(uniforms);
            byte[] transforms = copyDynamicTransforms(dynamicTransforms);
            byte[] globals = copyGlobalSettings(uniforms);
            if (projection == null || transforms == null || globals == null) {
                return null;
            }
            UniformPayload payload = payloadFor(pipeline, LINE_UNIFORM_SIZE);
            payload.putLineReplay(projection, transforms, globals);
            return new RenderPassDrawPacket.UniformBinding("radianceLineReplay",
                RenderPassDrawPacket.UniformKind.BUFFER, null, null,
                RenderPassNativeResourceMirror.rememberUniform(payload,
                    "radianceLineReplay", payload.address(), LINE_UNIFORM_SIZE,
                    "generic-replay-line-uniform"), null, true);
        }
        if (vertexFormatType == VERTEX_FORMAT_ENTITY) {
            if (!isSupportedEntityReplayPipeline(pipelineBinding)) {
                return null;
            }
            RenderPassDrawPacket.TextureBinding texture = firstTexture(textures);
            if (!hasNativeReplaySampler(texture)) {
                return null;
            }
            int textureId = nativeTextureId(texture);
            if (textureId <= 0) {
                return null;
            }
            mirrorSamplerState(textureId, texture);
            byte[] projection = copyProjectionMatrix(uniforms);
            byte[] transforms = copyDynamicTransforms(dynamicTransforms);
            if (projection == null || transforms == null) {
                return null;
            }
            UniformPayload payload = payloadFor(pipeline, ENTITY_UNIFORM_SIZE);
            payload.putEntityReplay(textureId, projection, transforms);
            return new RenderPassDrawPacket.UniformBinding("radianceEntityReplay",
                RenderPassDrawPacket.UniformKind.BUFFER, null, null,
                RenderPassNativeResourceMirror.rememberUniform(payload,
                    "radianceEntityReplay", payload.address(), ENTITY_UNIFORM_SIZE,
                    "generic-replay-entity-uniform"), null, true);
        }
        if (vertexFormatType != VERTEX_FORMAT_POSITION_TEX_COLOR) {
            return null;
        }

        RenderPassDrawPacket.TextureBinding texture = firstTexture(textures);
        if (!hasNativeReplaySampler(texture)) {
            return null;
        }
        int textureId = nativeTextureId(texture);
        if (textureId <= 0) {
            return null;
        }
        mirrorSamplerState(textureId, texture);
        UniformPayload payload = payloadFor(pipeline, TEXTURE_UNIFORM_SIZE);
        payload.putTextureId(textureId);
        return new RenderPassDrawPacket.UniformBinding("radianceSimpleTexture",
            RenderPassDrawPacket.UniformKind.BUFFER, null, null,
            RenderPassNativeResourceMirror.rememberUniform(payload,
                "radianceSimpleTexture", payload.address(), TEXTURE_UNIFORM_SIZE,
                "generic-replay-texture-uniform"), null, true);
    }

    static List<RenderPassDrawPacket.FallbackDetail> replayShaderBlockers(
        RenderPassDrawPacket packet) {
        if (packet == null || packet.nativeDrawCall() == null) {
            return List.of();
        }

        RenderPassDrawPacket.NativeDrawCall nativeDrawCall = packet.nativeDrawCall();
        RenderPassDrawPacket.PipelineBinding pipeline = packet.pipeline();
        int vertexFormatType = vertexFormatType(pipeline);
        boolean replayShader = isReplayShader(pipeline);
        if (vertexFormatType < 0) {
            return List.of(detail(RenderPassDrawPacket.FallbackReason.MISSING_VERTEX_FORMAT,
                "vertexFormatType", "native vertex format mapping is unavailable", true));
        }
        if (vertexFormatType == VERTEX_FORMAT_ENTITY
            && !isSupportedEntityReplayPipeline(pipeline)) {
            return List.of(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET,
                "pipeline.location",
                "ENTITY generic replay is bounded to vanilla cutout entity/item "
                    + "pipelines, captured pipeline="
                    + pipelineLocation(pipeline),
                false));
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH
            && !isSupportedLineReplayPipeline(pipeline)) {
            return List.of(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET,
                "pipeline.location",
                "line replay is bounded to minecraft:pipeline/lines, captured pipeline="
                    + pipelineLocation(pipeline),
                false));
        }
        if (nativeDrawCall.hasShaderId() && nativeDrawCall.hasUniformPayload()
            && !replayShader) {
            return List.of();
        }
        if (vertexFormatType != VERTEX_FORMAT_POSITION_COLOR
            && vertexFormatType != VERTEX_FORMAT_ENTITY
            && vertexFormatType != VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH
            && vertexFormatType != VERTEX_FORMAT_POSITION_TEX_COLOR) {
            return List.of(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET,
                "pipeline.vertexFormats",
                "full pipeline shader source is unavailable for PreparedRenderType replay; "
                    + "generic replay shaders only support POSITION_COLOR("
                    + VERTEX_FORMAT_POSITION_COLOR + "), ENTITY("
                    + VERTEX_FORMAT_ENTITY + "), POSITION_COLOR_NORMAL_LINE_WIDTH("
                    + VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH
                    + "), and POSITION_TEX_COLOR("
                    + VERTEX_FORMAT_POSITION_TEX_COLOR
                    + "), captured vertexFormatType="
                    + vertexFormatType + ", formats=" + vertexFormats(pipeline),
                false));
        }
        if (pipeline == null || pipeline.vertexFormats() == null
            || pipeline.vertexFormats().size() != 1) {
            return List.of(detail(
                RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET,
                "pipeline.vertexFormats",
                "generic replay shader requires exactly one simple vertex format binding, captured formats="
                    + vertexFormats(pipeline),
                false));
        }

        ArrayList<RenderPassDrawPacket.FallbackDetail> details = new ArrayList<>();
        if (!nativeDrawCall.hasShaderId()) {
            details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_SHADER_ID,
                "pipeline.nativeReference.shaderId",
                "generic replay shader id was not registered for "
                    + displayVertexFormat(vertexFormatType) + "; pipeline="
                    + pipelineLocation(pipeline),
                true));
        }

        if (vertexFormatType == VERTEX_FORMAT_POSITION_TEX_COLOR) {
            RenderPassDrawPacket.TextureBinding texture = firstTexture(packet.textures());
            if (nativeTextureId(texture) <= 0) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_TEXTURE_OR_SAMPLER,
                    "textures[0].nativeTextureId",
                    "POSITION_TEX_COLOR generic replay requires a captured texture with a native texture id",
                    true));
            } else if (!hasNativeReplaySampler(texture)) {
                details.add(detail(
                    RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET,
                    "textures[0].samplerState",
                    "POSITION_TEX_COLOR generic replay only supports the native overlay descriptor sampler shape; captured sampler="
                        + samplerDescription(texture),
                    false));
            }
        }
        if (vertexFormatType == VERTEX_FORMAT_ENTITY) {
            RenderPassDrawPacket.TextureBinding texture = firstTexture(packet.textures());
            if (nativeTextureId(texture) <= 0) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_TEXTURE_OR_SAMPLER,
                    "textures[0].nativeTextureId",
                    "ENTITY generic replay requires a captured texture with a native texture id",
                    true));
            } else if (!hasNativeReplaySampler(texture)) {
                details.add(detail(
                    RenderPassDrawPacket.FallbackReason.UNSUPPORTED_NATIVE_DRAW_PACKET,
                    "textures[0].samplerState",
                    "ENTITY generic replay only supports the native overlay descriptor sampler shape; captured sampler="
                        + samplerDescription(texture),
                    false));
            }
            if (copyProjectionMatrix(packet.uniforms()) == null) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_POINTER,
                    "uniforms.Projection",
                    "ENTITY generic replay requires the packet's mirrored 64-byte Projection uniform range",
                    true));
            }
            if (copyDynamicTransforms(packet.dynamicTransforms()) == null) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_POINTER,
                    "dynamicTransforms",
                    "ENTITY generic replay requires a mirrored 160-byte DynamicTransforms range",
                    true));
            }
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
            if (copyProjectionMatrix(packet.uniforms()) == null) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_POINTER,
                    "uniforms.Projection",
                    "line replay requires the packet's mirrored 64-byte Projection uniform range",
                    true));
            }
            if (copyDynamicTransforms(packet.dynamicTransforms()) == null) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_POINTER,
                    "dynamicTransforms",
                    "line replay requires a mirrored 160-byte DynamicTransforms range",
                    true));
            }
            if (copyGlobalSettings(packet.uniforms()) == null) {
                details.add(detail(RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_POINTER,
                    "uniforms.Globals",
                    "line replay requires a mirrored 64-byte Globals uniform range for ScreenSize",
                    true));
            }
        }

        int expectedUniformSize = uniformSize(vertexFormatType);
        if (nativeDrawCall.uniformSize() != expectedUniformSize) {
            details.add(detail(
                RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_SIZE,
                "uniforms.simpleReplay.uniformSize",
                displayVertexFormat(vertexFormatType) + " generic replay requires uniformSize="
                    + expectedUniformSize + ", captured uniformSize="
                    + nativeDrawCall.uniformSize(),
                true));
        } else if (expectedUniformSize > 0 && nativeDrawCall.uniformPtr() == 0L) {
            details.add(detail(
                RenderPassDrawPacket.FallbackReason.MISSING_NATIVE_UNIFORM_POINTER,
                "uniforms.simpleReplay.uniformPtr",
                displayVertexFormat(vertexFormatType)
                    + " generic replay requires a non-zero uniform payload pointer",
                true));
        }
        return details;
    }

    private static RenderPassDrawPacket.TextureBinding firstTexture(
        List<RenderPassDrawPacket.TextureBinding> textures) {
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        return textures.getFirst();
    }

    private static int nativeTextureId(RenderPassDrawPacket.TextureBinding texture) {
        if (texture != null && texture.nativeTextureIdAvailable()
            && texture.nativeTextureId() > 0) {
            return texture.nativeTextureId();
        }
        return 0;
    }

    private static boolean hasNativeReplaySampler(RenderPassDrawPacket.TextureBinding texture) {
        if (texture == null || texture.sampler() == null || texture.samplerState() == null) {
            return false;
        }
        RenderPassDrawPacket.SamplerBinding sampler = texture.samplerState();
        return supportedAddressMode(sampler.addressModeU())
            && supportedAddressMode(sampler.addressModeV())
            && String.valueOf(sampler.addressModeU()).equals(String.valueOf(sampler.addressModeV()))
            && supportedFilterMode(sampler.minFilter())
            && supportedFilterMode(sampler.magFilter())
            && String.valueOf(sampler.minFilter()).equals(String.valueOf(sampler.magFilter()))
            && sampler.maxAnisotropy() <= 1
            && (sampler.maxLod().isEmpty() || sampler.maxLod().orElse(0.0) == 0.0);
    }

    private static void mirrorSamplerState(int textureId,
        RenderPassDrawPacket.TextureBinding texture) {
        if (textureId <= 0 || texture == null || texture.samplerState() == null) {
            return;
        }
        RenderPassDrawPacket.SamplerBinding sampler = texture.samplerState();
        TextureProxy.setFilter(textureId, vkFilter(sampler.minFilter()), vkMipmapMode(
            sampler.minFilter()));
        TextureProxy.setClamp(textureId, vkAddressMode(sampler.addressModeU()));
    }

    private static boolean supportedAddressMode(Object mode) {
        String name = String.valueOf(mode);
        return "REPEAT".equals(name) || "CLAMP_TO_EDGE".equals(name);
    }

    private static boolean supportedFilterMode(Object mode) {
        String name = String.valueOf(mode);
        return "NEAREST".equals(name) || "LINEAR".equals(name);
    }

    private static int vkFilter(Object mode) {
        return "LINEAR".equals(String.valueOf(mode))
            ? VulkanConstants.VkFilter.VK_FILTER_LINEAR.getValue()
            : VulkanConstants.VkFilter.VK_FILTER_NEAREST.getValue();
    }

    private static int vkMipmapMode(Object mode) {
        return "LINEAR".equals(String.valueOf(mode))
            ? VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_LINEAR.getValue()
            : VulkanConstants.VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST.getValue();
    }

    private static int vkAddressMode(Object mode) {
        return "CLAMP_TO_EDGE".equals(String.valueOf(mode))
            ? VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE.getValue()
            : VulkanConstants.VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_REPEAT.getValue();
    }

    private static String samplerDescription(RenderPassDrawPacket.TextureBinding texture) {
        return texture == null || texture.samplerState() == null
            ? "<missing>"
            : texture.samplerState().toString();
    }

    private static int vertexFormatType(RenderPassDrawPacket.PipelineBinding pipelineBinding) {
        if (pipelineBinding == null || pipelineBinding.vertexFormats() == null
            || pipelineBinding.vertexFormats().isEmpty()
            || pipelineBinding.vertexFormats().getFirst() == null) {
            return -1;
        }
        return DrawCommandProxy.RenderPass.vertexFormatType(
            pipelineBinding.vertexFormats().getFirst());
    }

    private static boolean isReplayShader(RenderPassDrawPacket.PipelineBinding pipeline) {
        if (pipeline == null || pipeline.nativeReference() == null
            || pipeline.nativeReference().source() == null) {
            return false;
        }
        String source = pipeline.nativeReference().source();
        return source.startsWith("generic-replay-") || source.startsWith("simple-gui-");
    }

    private static boolean isSupportedEntityReplayPipeline(RenderPipeline pipeline) {
        return pipeline != null && isSupportedEntityReplayPipeline(pipeline.getLocation());
    }

    private static boolean isSupportedEntityReplayPipeline(
        RenderPassDrawPacket.PipelineBinding pipeline) {
        return pipeline != null && isSupportedEntityReplayPipeline(pipeline.location());
    }

    private static boolean isSupportedEntityReplayPipeline(Identifier location) {
        return ENTITY_CUTOUT_PIPELINE.equals(location)
            || ENTITY_CUTOUT_CULL_PIPELINE.equals(location)
            || ARMOR_CUTOUT_NO_CULL_PIPELINE.equals(location)
            || ITEM_CUTOUT_PIPELINE.equals(location);
    }

    private static boolean isSupportedLineReplayPipeline(RenderPipeline pipeline) {
        return pipeline != null && LINES_PIPELINE.equals(pipeline.getLocation());
    }

    private static boolean isSupportedLineReplayPipeline(
        RenderPassDrawPacket.PipelineBinding pipeline) {
        return pipeline != null && LINES_PIPELINE.equals(pipeline.location());
    }

    private static boolean isSupportedDrawMode(int drawMode, int vertexFormatType) {
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
            return drawMode == DrawCommandProxy.RenderPass.DRAW_MODE_LINES;
        }
        return drawMode == DrawCommandProxy.RenderPass.DRAW_MODE_TRIANGLES
            || drawMode == DrawCommandProxy.RenderPass.DRAW_MODE_QUADS;
    }

    private static String vertexFormats(RenderPassDrawPacket.PipelineBinding pipeline) {
        return pipeline == null || pipeline.vertexFormats() == null
            ? "<missing>"
            : pipeline.vertexFormats().toString();
    }

    private static String pipelineLocation(RenderPassDrawPacket.PipelineBinding pipeline) {
        return pipeline == null || pipeline.location() == null
            ? "<missing>"
            : pipeline.location().toString();
    }

    private static String displayVertexFormat(int vertexFormatType) {
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR) {
            return "POSITION_COLOR";
        }
        if (vertexFormatType == VERTEX_FORMAT_ENTITY) {
            return "ENTITY";
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
            return "POSITION_COLOR_NORMAL_LINE_WIDTH";
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_TEX_COLOR) {
            return "POSITION_TEX_COLOR";
        }
        return "vertexFormatType=" + vertexFormatType;
    }

    private static int uniformSize(int vertexFormatType) {
        if (vertexFormatType == VERTEX_FORMAT_POSITION_TEX_COLOR) {
            return TEXTURE_UNIFORM_SIZE;
        }
        if (vertexFormatType == VERTEX_FORMAT_ENTITY) {
            return ENTITY_UNIFORM_SIZE;
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
            return LINE_UNIFORM_SIZE;
        }
        return 0;
    }

    private static RenderPassDrawPacket.FallbackDetail detail(
        RenderPassDrawPacket.FallbackReason reason, String field, String message,
        boolean retryable) {
        return RenderPassDrawPacket.FallbackDetail.of(reason, field, message, retryable);
    }

    private static UniformPayload payloadFor(RenderPipeline pipeline, int size) {
        synchronized (UNIFORM_PAYLOADS) {
            UniformPayload existing = UNIFORM_PAYLOADS.get(pipeline);
            if (existing != null && existing.size() == size) {
                return existing;
            }
            UniformPayload created = new UniformPayload(size);
            UNIFORM_PAYLOADS.put(pipeline, created);
            return created;
        }
    }

    private static ShaderFiles ensureShaderFiles(int vertexFormatType) throws IOException {
        if (RadianceClient.radianceDir == null) {
            throw new IOException("Radiance directory is not initialized");
        }
        Path shaderDir = RadianceClient.radianceDir.resolve("temp")
            .resolve("render-pass-replay");
        Files.createDirectories(shaderDir);

        if (vertexFormatType == VERTEX_FORMAT_POSITION_TEX_COLOR) {
            Path vertex = shaderDir.resolve("simple_position_tex_color.vert");
            Path fragment = shaderDir.resolve("simple_position_tex_color.frag");
            writeIfChanged(vertex, TEXTURE_VERTEX_SHADER);
            writeIfChanged(fragment, TEXTURE_FRAGMENT_SHADER);
            return new ShaderFiles(vertex, fragment);
        }
        if (vertexFormatType == VERTEX_FORMAT_ENTITY) {
            Path vertex = shaderDir.resolve("entity_position_color_tex_overlay_light_normal.vert");
            Path fragment = shaderDir.resolve("entity_position_color_tex_overlay_light_normal.frag");
            writeIfChanged(vertex, ENTITY_VERTEX_SHADER);
            writeIfChanged(fragment, ENTITY_FRAGMENT_SHADER);
            return new ShaderFiles(vertex, fragment);
        }
        if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
            Path vertex = shaderDir.resolve("line_position_color_normal_line_width.vert");
            Path fragment = shaderDir.resolve("line_position_color_normal_line_width.frag");
            writeIfChanged(vertex, LINE_VERTEX_SHADER);
            writeIfChanged(fragment, LINE_FRAGMENT_SHADER);
            return new ShaderFiles(vertex, fragment);
        }

        Path vertex = shaderDir.resolve("simple_position_color.vert");
        Path fragment = shaderDir.resolve("simple_position_color.frag");
        writeIfChanged(vertex, COLOR_VERTEX_SHADER);
        writeIfChanged(fragment, COLOR_FRAGMENT_SHADER);
        return new ShaderFiles(vertex, fragment);
    }

    private static void writeIfChanged(Path path, String content) throws IOException {
        if (Files.exists(path) && Files.readString(path, StandardCharsets.UTF_8)
            .equals(content)) {
            return;
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String shaderKey(RenderPipeline pipeline, int vertexFormatType, int drawMode,
        int uniformSize) {
        Identifier location = pipeline.getLocation();
        String path = location == null ? "unknown" : location.toString()
            .replaceAll("[^a-zA-Z0-9._-]", "_");
        return "radiance_replay_" + path + "_" + vertexFormatType + "_" + drawMode + "_"
            + uniformSize;
    }

    private record ShaderFiles(Path vertex, Path fragment) {
    }

    private static final class UniformPayload {

        private final ByteBuffer bytes;

        private UniformPayload(int size) {
            this.bytes = size <= 0 ? null : ByteBuffer.allocateDirect(size)
                .order(ByteOrder.nativeOrder());
        }

        private long address() {
            return bytes == null ? 0L : MemoryUtil.memAddress(bytes);
        }

        private int size() {
            return bytes == null ? 0 : bytes.capacity();
        }

        private void putTextureId(int textureId) {
            if (bytes == null) {
                return;
            }
            bytes.clear();
            for (int i = 0; i < bytes.capacity(); i++) {
                bytes.put(i, (byte) 0);
            }
            bytes.putInt(0, textureId);
        }

        private void putEntityReplay(int textureId, byte[] projection,
            byte[] dynamicTransforms) {
            if (bytes == null || projection == null || projection.length != MAT4_BYTES
                || dynamicTransforms == null
                || dynamicTransforms.length != DYNAMIC_TRANSFORMS_BYTES) {
                return;
            }
            bytes.clear();
            for (int i = 0; i < bytes.capacity(); i++) {
                bytes.put(i, (byte) 0);
            }
            bytes.putInt(0, textureId);
            putBytes(16, projection);
            putBytes(80, dynamicTransforms, 0, 64);
            putBytes(144, dynamicTransforms, 64, 16);
            putBytes(160, dynamicTransforms, 80, 16);
            putBytes(176, dynamicTransforms, 96, 64);
        }

        private void putLineReplay(byte[] projection, byte[] dynamicTransforms,
            byte[] globals) {
            if (bytes == null || projection == null || projection.length != MAT4_BYTES
                || dynamicTransforms == null
                || dynamicTransforms.length != DYNAMIC_TRANSFORMS_BYTES
                || globals == null || globals.length != GLOBALS_BYTES) {
                return;
            }
            bytes.clear();
            for (int i = 0; i < bytes.capacity(); i++) {
                bytes.put(i, (byte) 0);
            }
            putBytes(0, projection);
            putBytes(MAT4_BYTES, dynamicTransforms);
            putBytes(MAT4_BYTES + DYNAMIC_TRANSFORMS_BYTES, globals);
        }

        private void putBytes(int offset, byte[] source) {
            putBytes(offset, source, 0, source.length);
        }

        private void putBytes(int offset, byte[] source, int sourceOffset, int size) {
            ByteBuffer view = bytes.duplicate();
            view.position(offset);
            view.put(source, sourceOffset, size);
        }
    }

    private static byte[] copyProjectionMatrix(
        List<RenderPassDrawPacket.UniformBinding> uniforms) {
        if (uniforms == null) {
            return null;
        }
        RenderPassDrawPacket.UniformBinding projection = uniforms
            .stream()
            .filter(uniform -> uniform != null && "Projection".equals(uniform.name()))
            .findFirst()
            .orElse(null);
        if (projection == null || projection.slice() == null
            || projection.slice().slice() == null) {
            return null;
        }
        return BufferProxy.copyMirroredBufferRange(projection.slice().slice(), MAT4_BYTES);
    }

    private static byte[] copyDynamicTransforms(
        RenderPassDrawPacket.BufferSliceBinding dynamicTransforms) {
        if (dynamicTransforms == null || dynamicTransforms.slice() == null) {
            return null;
        }
        return BufferProxy.copyMirroredBufferRange(dynamicTransforms.slice(),
            DYNAMIC_TRANSFORMS_BYTES);
    }

    private static byte[] copyGlobalSettings(
        List<RenderPassDrawPacket.UniformBinding> uniforms) {
        if (uniforms == null) {
            return null;
        }
        RenderPassDrawPacket.UniformBinding globals = uniforms
            .stream()
            .filter(uniform -> uniform != null && "Globals".equals(uniform.name()))
            .findFirst()
            .orElse(null);
        if (globals == null || globals.buffer() == null || globals.buffer().buffer() == null) {
            return null;
        }
        return BufferProxy.copyMirroredBufferRange(globals.buffer().buffer(), 0, GLOBALS_BYTES);
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

    private static final String LINE_VERTEX_SHADER = """
        #version 460

        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec4 inColor;
        layout(location = 2) in vec3 inNormal;
        layout(location = 3) in float inLineWidth;

        layout(std140, set = 1, binding = 0) uniform OverlayDrawUniform {
            mat4 ProjMat;
            mat4 ModelViewMat;
            vec4 ColorModulator;
            vec4 ModelOffset;
            mat4 TextureMat;
            ivec3 CameraBlockPos;
            vec3 CameraOffset;
            vec2 ScreenSize;
            float GlintAlpha;
            float GameTime;
            int MenuBlurRadius;
            int UseRgss;
        };

        layout(location = 0) out vec4 fragColor;

        const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
        const mat4 VIEW_SCALE = mat4(
            VIEW_SHRINK, 0.0, 0.0, 0.0,
            0.0, VIEW_SHRINK, 0.0, 0.0,
            0.0, 0.0, VIEW_SHRINK, 0.0,
            0.0, 0.0, 0.0, 1.0
        );

        void main() {
            vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(inPosition, 1.0);
            vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(inPosition + inNormal, 1.0);
            vec3 ndc1 = linePosStart.xyz / linePosStart.w;
            vec3 ndc2 = linePosEnd.xyz / linePosEnd.w;
            vec2 screenSize = max(abs(ScreenSize), vec2(1.0));
            vec2 lineDirection = (ndc2.xy - ndc1.xy) * screenSize;
            float directionLength = length(lineDirection);
            if (directionLength < 0.00001) {
                lineDirection = vec2(1.0, 0.0);
            } else {
                lineDirection /= directionLength;
            }
            vec2 lineOffset = vec2(-lineDirection.y, lineDirection.x)
                * max(abs(inLineWidth), 1.0) / screenSize;
            if (lineOffset.x < 0.0) {
                lineOffset *= -1.0;
            }
            if ((gl_VertexIndex & 1) == 0) {
                gl_Position = vec4((ndc1 + vec3(lineOffset, 0.0)) * linePosStart.w,
                    linePosStart.w);
            } else {
                gl_Position = vec4((ndc1 - vec3(lineOffset, 0.0)) * linePosStart.w,
                    linePosStart.w);
            }
            gl_Position.y = -gl_Position.y;
            fragColor = inColor * ColorModulator;
        }
        """;

    private static final String LINE_FRAGMENT_SHADER = """
        #version 460

        layout(location = 0) in vec4 fragColor;
        layout(location = 0) out vec4 outColor;

        void main() {
            outColor = fragColor;
        }
        """;

    private static final String ENTITY_VERTEX_SHADER = """
        #version 460

        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec4 inColor;
        layout(location = 2) in vec2 inUv0;

        layout(std140, set = 1, binding = 0) uniform OverlayDrawUniform {
            ivec4 replayParams;
            mat4 ProjMat;
            mat4 ModelViewMat;
            vec4 ColorModulator;
            vec4 ModelOffset;
            mat4 TextureMat;
        };

        layout(location = 0) out vec2 fragUv;
        layout(location = 1) out vec4 fragColor;

        void main() {
            vec3 position = inPosition + ModelOffset.xyz;
            gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
            gl_Position.y = -gl_Position.y;
            fragUv = (TextureMat * vec4(inUv0, 0.0, 1.0)).xy;
            fragColor = inColor * ColorModulator;
        }
        """;

    private static final String ENTITY_FRAGMENT_SHADER = """
        #version 460
        #extension GL_EXT_nonuniform_qualifier : enable

        layout(set = 0, binding = 0) uniform sampler2D OverlayTextures[4096];
        layout(std140, set = 1, binding = 0) uniform OverlayDrawUniform {
            ivec4 replayParams;
            mat4 ProjMat;
            mat4 ModelViewMat;
            vec4 ColorModulator;
            vec4 ModelOffset;
            mat4 TextureMat;
        };

        layout(location = 0) in vec2 fragUv;
        layout(location = 1) in vec4 fragColor;
        layout(location = 0) out vec4 outColor;

        void main() {
            int textureId = replayParams.x;
            vec4 texel = texture(OverlayTextures[nonuniformEXT(textureId)], fragUv);
            outColor = texel * fragColor;
        }
        """;
}
