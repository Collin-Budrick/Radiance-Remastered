package com.radiance.client.proxy.world;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.ShaderProxy;
import com.radiance.client.state.RenderSystemStateBridge;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

final class CloudReplayShaderBridge {

    private static final int VERTEX_FORMAT_POSITION_COLOR = 4;
    private static final int DRAW_MODE_QUADS = 7;
    private static final int MAT4_BYTES = 64;
    private static final int UNIFORM_SIZE = MAT4_BYTES * 2;

    private static volatile Integer shaderId;
    private static volatile boolean disabled;
    private static final ThreadLocal<ByteBuffer> UNIFORM_BUFFER =
        ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(UNIFORM_SIZE)
            .order(ByteOrder.nativeOrder()));
    private static final AtomicBoolean LOGGED_SHADER = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_BLOCKER = new AtomicBoolean();

    private CloudReplayShaderBridge() {
    }

    static NativeDrawState prepareNativeDrawState(CloudProxy.EncodedCloudPacket packet) {
        if (packet == null || disabled || !RendererAvailability.isRendererLifecycleActive()) {
            return null;
        }

        try {
            int nativeShaderId = ensureShader();
            if (nativeShaderId < 0) {
                return null;
            }

            byte[] projection = copyProjectionMatrix();
            if (projection == null || projection.length != MAT4_BYTES) {
                logBlocker("missing mirrored 26.2 Projection uniform for native cloud replay", null);
                return null;
            }

            ByteBuffer uniform = UNIFORM_BUFFER.get();
            uniform.clear();
            uniform.put(projection);
            Matrix4f modelView = RenderSystemStateBridge.modelViewMatrixCopy();
            modelView.get(MAT4_BYTES, uniform);
            uniform.position(0);
            uniform.limit(UNIFORM_SIZE);
            return new NativeDrawState(nativeShaderId, uniform);
        } catch (IOException | RuntimeException | LinkageError exception) {
            disabled = true;
            logBlocker("cloud replay shader bridge failed; native clouds will fall back", exception);
            return null;
        }
    }

    private static int ensureShader() throws IOException {
        Integer cached = shaderId;
        if (cached != null) {
            return cached;
        }
        if (RadianceClient.radianceDir == null) {
            logBlocker("Radiance directory is not initialized for cloud replay shader files", null);
            return -1;
        }

        Path shaderDir = RadianceClient.radianceDir.resolve("temp").resolve("cloud-replay");
        Files.createDirectories(shaderDir);
        Path vertex = shaderDir.resolve("encoded_cloud_position_color.vert");
        Path fragment = shaderDir.resolve("encoded_cloud_position_color.frag");
        writeIfChanged(vertex, CLOUD_VERTEX_SHADER);
        writeIfChanged(fragment, CLOUD_FRAGMENT_SHADER);

        int registered = ShaderProxy.registerShader("radiance_encoded_cloud_replay_26_2",
            VERTEX_FORMAT_POSITION_COLOR, DRAW_MODE_QUADS, UNIFORM_SIZE, vertex.toString(),
            fragment.toString(), new String[0], new String[0]);
        if (registered >= 0) {
            shaderId = registered;
            if (LOGGED_SHADER.compareAndSet(false, true)) {
                RadianceClient.LOGGER.info(
                    "Radiance cloud replay shader bridge: registered shader id={} vertexFormat={} drawMode={} uniformSize={}",
                    registered, VERTEX_FORMAT_POSITION_COLOR, DRAW_MODE_QUADS, UNIFORM_SIZE);
            }
        }
        return registered;
    }

    private static byte[] copyProjectionMatrix() {
        RenderSystemStateBridge.DefaultUniformBindings defaults =
            RenderSystemStateBridge.defaultUniformBindings();
        GpuBufferSlice projection = defaults == null ? null : defaults.projection();
        if (projection == null) {
            projection = RenderSystemStateBridge.projectionMatrixBuffer();
        }
        if (projection == null) {
            return null;
        }
        return BufferProxy.copyMirroredBufferRange(projection, MAT4_BYTES);
    }

    private static void writeIfChanged(Path path, String content) throws IOException {
        if (Files.exists(path) && Files.readString(path, StandardCharsets.UTF_8).equals(content)) {
            return;
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void logBlocker(String message, Throwable throwable) {
        if (!LOGGED_BLOCKER.compareAndSet(false, true)) {
            return;
        }
        if (throwable == null) {
            RadianceClient.LOGGER.warn(
                "Radiance cloud replay shader bridge: {}; leaving vanilla clouds active", message);
        } else {
            RadianceClient.LOGGER.warn(
                "Radiance cloud replay shader bridge: {}; leaving vanilla clouds active", message,
                throwable);
        }
    }

    record NativeDrawState(int shaderId, ByteBuffer uniform) {

        long uniformAddress() {
            return MemoryUtil.memAddress(uniform);
        }

        int uniformSize() {
            return UNIFORM_SIZE;
        }
    }

    private static final String CLOUD_VERTEX_SHADER = """
        #version 460

        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec4 inColor;

        layout(std140, set = 1, binding = 0) uniform OverlayDrawUniform {
            mat4 ProjMat;
            mat4 ModelViewMat;
        };

        layout(location = 0) out vec4 fragColor;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(inPosition, 1.0);
            gl_Position.y = -gl_Position.y;
            fragColor = inColor;
        }
        """;

    private static final String CLOUD_FRAGMENT_SHADER = """
        #version 460

        layout(location = 0) in vec4 fragColor;
        layout(location = 0) out vec4 outColor;

        void main() {
            outColor = fragColor;
        }
        """;
}
