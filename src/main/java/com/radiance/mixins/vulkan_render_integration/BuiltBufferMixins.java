package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.vertex.PBRVertexFormatElements;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MeshData.class)
public class BuiltBufferMixins {

    @Inject(method = "decodeQuadCentroids(Ljava/nio/ByteBuffer;ILcom/mojang/blaze3d/vertex/VertexFormat;Lcom/mojang/blaze3d/vertex/CompactVectorArray;I)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private static void addPBRPosition(ByteBuffer buf, int vertexCount, VertexFormat format,
        CompactVectorArray output, int outputOffset, CallbackInfo ci) {
        int i = 0;
        if (format.getElement(PBRVertexFormatElements.PBR_POS.name()) != null) {
            i = PBRVertexFormatElements.PBR_POS.offset() / 4;
        }
        if (format.getElement("Position") == null && format.getElement(PBRVertexFormatElements.PBR_POS.name()) == null) {
            throw new IllegalArgumentException(
                "Cannot identify quad centers with no position element");
        } else {
            FloatBuffer floatBuffer = buf.asFloatBuffer();
            int j = format.getVertexSize() / 4;
            int k = j * 4;
            int l = vertexCount / 4;

            for (int m = 0; m < l; m++) {
                int n = m * k + i;
                int o = n + j * 2;
                float f = floatBuffer.get(n);
                float g = floatBuffer.get(n + 1);
                float h = floatBuffer.get(n + 2);
                float p = floatBuffer.get(o);
                float q = floatBuffer.get(o + 1);
                float r = floatBuffer.get(o + 2);
                output.set(outputOffset + m, (f + p) / 2.0F, (g + q) / 2.0F,
                    (h + r) / 2.0F);
            }

            ci.cancel();
        }
    }
}
