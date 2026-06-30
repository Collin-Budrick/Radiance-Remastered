package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.PrimitiveTopology;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGlUniformExt;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class ShaderProxy {

    private static final Identifier WHITE_TEXTURE_ID = Identifier.fromNamespaceAndPath("radiance",
        "generated/white");
    private static Integer whiteTextureId;

    private ShaderProxy() {
    }

    public static native int registerShader(String shaderKey, int vertexFormatType,
        int drawMode, int uniformSize, String vertexShaderPath, String fragmentShaderPath,
        String[] defineNames, String[] defineValues);

    public static native void draw(int vertexId, int indexId, int shaderId, int indexCount,
        int indexType, long uniformPtr, int uniformSize);

    public static void draw(BufferProxy.VertexIndexBufferHandle handle, int shaderId, int indexCount,
        int indexType, long uniformPtr, int uniformSize) {
        draw(handle.vertexId, handle.indexId, shaderId, indexCount, indexType, uniformPtr,
            uniformSize);
    }

    public static UniformHandle createUniform(Object shader, Object shaderProgram,
        MemoryStack stack) {
        int uniformBufferSize = intValue(invoke(shader, "uniformBufferSize"));
        ByteBuffer bb = stack.calloc(uniformBufferSize);
        int uniformIndex = 0;
        for (Object field : iterableValue(invoke(shader, "fields"))) {
            if (boolValue(invoke(field, "isSampler"))) {
                bb.putInt(intValue(invoke(field, "offset")),
                    resolveSamplerTextureId(shaderProgram, field));
                continue;
            }
            Uniform uniform = (Uniform) listValue(invoke(shaderProgram,
                "radiance$getUniformsValue")).get(uniformIndex++);
            putUniform(bb, field, uniform);
        }
        return new UniformHandle(MemoryUtil.memAddress(bb), uniformBufferSize);
    }

    public static void syncState(Object shaderProgram, PrimitiveTopology drawMode) {
        // Shader state initialization moved into the 26.2 render pipeline.
    }

    public record UniformHandle(long addr, int size) {

    }

    private static int resolveSamplerTextureId(Object shaderProgram, Object field) {
        String fieldName = stringValue(invoke(field, "name"));
        Object2IntMap<String> samplerTextures = object2IntMapValue(invoke(shaderProgram,
            "radiance$getSamplerTexturesValue"));
        if (samplerTextures.containsKey(fieldName)) {
            int textureId = samplerTextures.getInt(fieldName);
            if (textureId != 0) {
                return textureId;
            }
        }
        if ("Sampler2".equals(fieldName)) {
            return getWhiteTextureId();
        }
        return 0;
    }

    public static int getWhiteTextureId() {
        Integer cached = whiteTextureId;
        if (cached != null) {
            return cached;
        }

        NativeImage image = new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixel(x, y, 0xFFFFFFFF);
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "radiance/generated/white", image);
        Minecraft.getInstance()
            .getTextureManager()
            .register(WHITE_TEXTURE_ID, texture);
        whiteTextureId = 0;
        return whiteTextureId;
    }

    private static Integer tryParseSamplerSlot(String samplerName) {
        if (!samplerName.startsWith("Sampler")) {
            return null;
        }
        try {
            return Integer.parseInt(samplerName.substring("Sampler".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void putUniform(ByteBuffer bb, Object field, Uniform uniform) {
        IGlUniformExt ext = (IGlUniformExt) (Object) uniform;
        String kind = stringValue(invoke(invoke(field, "kind"), "name"));
        int offset = intValue(invoke(field, "offset"));
        int componentCount = intValue(invoke(field, "componentCount"));
        String fieldName = stringValue(invoke(field, "name"));
        switch (kind) {
            case "INT" -> putInts(bb, offset, ext.radiance$getIntDataValue(), componentCount);
            case "FLOAT" -> putFloats(bb, offset, ext.radiance$getFloatDataValue(), componentCount);
            case "MATRIX" -> putMatrix(bb, offset, componentCount, fieldName,
                ext.radiance$getFloatDataValue());
            case "SAMPLER" -> throw new IllegalStateException("Sampler fields are written separately");
            default -> throw new IllegalStateException("Unknown shader field kind " + kind);
        }
    }

    private static void putInts(ByteBuffer bb, int offset, IntBuffer values, int componentCount) {
        for (int i = 0; i < componentCount; i++) {
            bb.putInt(offset + i * Integer.BYTES, values.get(i));
        }
    }

    private static void putFloats(ByteBuffer bb, int offset, FloatBuffer values, int componentCount) {
        for (int i = 0; i < componentCount; i++) {
            bb.putFloat(offset + i * Float.BYTES, values.get(i));
        }
    }

    private static void putMatrix(ByteBuffer bb, int offset, int dimension, String uniformName,
        FloatBuffer values) {
        if (dimension == 4) {
            float[] matrix = new float[16];
            for (int i = 0; i < 16; i++) {
                matrix[i] = values.get(i);
            }
            if ("ProjMat".equals(uniformName)) {
                mapProjectionMatrix(matrix);
            }
            for (int i = 0; i < 16; i++) {
                bb.putFloat(offset + i * Float.BYTES, matrix[i]);
            }
            return;
        }

        int columnStride = Float.BYTES * 4;
        for (int column = 0; column < dimension; column++) {
            for (int row = 0; row < dimension; row++) {
                bb.putFloat(offset + column * columnStride + row * Float.BYTES,
                    values.get(column * dimension + row));
            }
        }
    }

    private static void mapProjectionMatrix(float[] matrix) {
        for (int column = 0; column < 4; column++) {
            int base = column * 4;
            float row0 = matrix[base];
            float row1 = matrix[base + 1];
            float row2 = matrix[base + 2];
            float row3 = matrix[base + 3];
            matrix[base] = row0;
            matrix[base + 1] = -row1;
            matrix[base + 2] = row2 * 0.5F + row3 * 0.5F;
            matrix[base + 3] = row3;
        }
    }

    private static Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Unable to call " + methodName + " on " + target, e);
        }
    }

    private static int intValue(Object value) {
        return ((Number) value).intValue();
    }

    private static boolean boolValue(Object value) {
        return (Boolean) value;
    }

    private static String stringValue(Object value) {
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<Object> iterableValue(Object value) {
        return (Iterable<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> listValue(Object value) {
        return (java.util.List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Object2IntMap<String> object2IntMapValue(Object value) {
        return (Object2IntMap<String>) value;
    }
}
