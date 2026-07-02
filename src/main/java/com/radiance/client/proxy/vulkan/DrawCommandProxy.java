package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.VulkanConstants;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

public class DrawCommandProxy {

    public static class Overlay {

        // region <vulkan>
        public static native void vkCmdClearEntireColorAttachment();

        public static native void vkCmdClearEntireDepthStencilAttachment(int mask);
        // endregion

        // region <openGL>
        public static void glClear(int mask) {
            if ((mask & GL11.GL_COLOR_BUFFER_BIT) > 0) {
                vkCmdClearEntireColorAttachment();
            }

            int vkMask = 0;
            if ((mask & GL11.GL_DEPTH_BUFFER_BIT) > 0) {
                vkMask |= VulkanConstants.VkImageAspectFlagBits.ofGL(GL11.GL_DEPTH_BUFFER_BIT);
            }
            if ((mask & GL11.GL_STENCIL_BUFFER_BIT) > 0) {
                vkMask |= VulkanConstants.VkImageAspectFlagBits.ofGL(GL11.GL_STENCIL_BUFFER_BIT);
            }
            if (vkMask > 0) {
                vkCmdClearEntireDepthStencilAttachment(vkMask);
            }
        }
        // endregion
    }

    public static final class RenderPass {

        public static final int TARGET_SOLID_OPAQUE = 0;
        public static final int TARGET_ITEM_ENTITY = 1;
        public static final int TARGET_NON_OPAQUE_ENTITY = 2;

        public static final int FLAG_SOLID = 1;
        public static final int FLAG_OPAQUE = 1 << 1;
        public static final int FLAG_INDEXED = 1 << 2;

        public static final int DRAW_MODE_LINES = 0;
        public static final int DRAW_MODE_TRIANGLES = 4;
        public static final int DRAW_MODE_QUADS = 7;

        public static final int VERTEX_FORMAT_ENTITY = 1;
        public static final int VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH = 5;
        public static final int VERTEX_FORMAT_POSITION_COLOR_LINE_WIDTH =
            VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH;

        public static final int INDEX_TYPE_SHORT = 0;
        public static final int INDEX_TYPE_INT = 1;

        public static final int STATUS_REPLAYED = 1;
        public static final int STATUS_DROPPED_NO_NATIVE = 0;
        public static final int STATUS_DROPPED_UNSUPPORTED = -1;
        public static final int STATUS_DROPPED_INVALID = -2;
        public static final int STATUS_DROPPED_NATIVE_ERROR = -3;
        public static final int STATUS_REPLAYED_NATIVE = STATUS_REPLAYED;
        public static final int STATUS_FALLBACK_UNSUPPORTED = STATUS_DROPPED_UNSUPPORTED;
        public static final int STATUS_FALLBACK_INVALIDATED = STATUS_DROPPED_INVALID;

        private RenderPass() {
        }

        private static native int vkCmdReplayDrawPacket(int target, int flags,
            boolean scissorEnabled, int scissorX, int scissorY, int scissorWidth,
            int scissorHeight, int vertexBufferId, int indexBufferId, int shaderId,
            int vertexFormatType, int drawMode, int indexType, int indexCount,
            int firstIndex, int vertexOffset, int firstInstance, int instanceCount,
            boolean blendEnabled, int srcColorBlendFactor, int srcAlphaBlendFactor,
            int dstColorBlendFactor, int dstAlphaBlendFactor, int colorBlendOp,
            int alphaBlendOp, int colorWriteMask, boolean depthTestEnabled,
            boolean depthWriteEnabled, int depthCompareOp, boolean depthBiasEnabled,
            float depthBiasSlopeFactor, float depthBiasConstantFactor, int cullMode,
            long uniformPtr, int uniformSize, long vertexPayloadPtr, int vertexPayloadSize,
            long indexPayloadPtr, int indexPayloadSize);

        public static int tryReplayDrawPacket(DrawPacket packet) {
            int shapeStatus = validateReplayShape(packet);
            if (shapeStatus != STATUS_REPLAYED) {
                return shapeStatus;
            }

            try {
                return vkCmdReplayDrawPacket(packet.target(), packet.flags(), packet.scissorEnabled(),
                    packet.scissorX(), packet.scissorY(), packet.scissorWidth(), packet.scissorHeight(),
                    packet.vertexBufferId(), packet.indexBufferId(), packet.shaderId(),
                    packet.vertexFormatType(), packet.drawMode(), packet.indexType(), packet.indexCount(),
                    packet.firstIndex(), packet.vertexOffset(), packet.firstInstance(), packet.instanceCount(),
                    packet.blendEnabled(), packet.srcColorBlendFactor(), packet.srcAlphaBlendFactor(),
                    packet.dstColorBlendFactor(), packet.dstAlphaBlendFactor(), packet.colorBlendOp(),
                    packet.alphaBlendOp(), packet.colorWriteMask(), packet.depthTestEnabled(),
                    packet.depthWriteEnabled(), packet.depthCompareOp(), packet.depthBiasEnabled(),
                    packet.depthBiasSlopeFactor(), packet.depthBiasConstantFactor(), packet.cullMode(),
                    packet.uniformPtr(), packet.uniformSize(),
                    payloadAddress(packet.vertexPayload()), payloadSize(packet.vertexPayload()),
                    payloadAddress(packet.indexPayload()), payloadSize(packet.indexPayload()));
            } catch (UnsatisfiedLinkError ignored) {
                return STATUS_DROPPED_NO_NATIVE;
            } catch (RuntimeException ignored) {
                return STATUS_DROPPED_NATIVE_ERROR;
            }
        }

        public static int validateReplayShape(DrawPacket packet) {
            if (packet == null || !hasBuffer(packet.vertexBufferId(), packet.vertexPayload())
                || !hasBuffer(packet.indexBufferId(), packet.indexPayload())
                || packet.shaderId() < 0 || packet.vertexFormatType() < 0 || packet.indexCount() < 1
                || packet.uniformSize() < 0 || (packet.uniformSize() > 0 && packet.uniformPtr() == 0L)) {
                return STATUS_DROPPED_INVALID;
            }
            if (packet.scissorEnabled()
                && (packet.scissorWidth() <= 0 || packet.scissorHeight() <= 0)) {
                return STATUS_DROPPED_INVALID;
            }
            if (!isSupportedTargetAndFlags(packet)
                || !isSupportedDrawMode(packet.drawMode(), packet.vertexFormatType(),
                    packet.target())
                || !isSupportedIndexType(packet.indexType())
                || packet.firstIndex() < 0 || packet.firstInstance() < 0
                || packet.instanceCount() < 1) {
                return STATUS_DROPPED_UNSUPPORTED;
            }
            return STATUS_REPLAYED;
        }

        public static int flags(boolean solid, boolean opaque, boolean indexed) {
            int flags = 0;
            if (solid) {
                flags |= FLAG_SOLID;
            }
            if (opaque) {
                flags |= FLAG_OPAQUE;
            }
            if (indexed) {
                flags |= FLAG_INDEXED;
            }
            return flags;
        }

        public static int lineFlags() {
            return FLAG_INDEXED;
        }

        public static int drawModeValue(PrimitiveTopology topology) {
            if (topology == null) {
                return -1;
            }
            try {
                return Constants.DrawModes.getValue(topology);
            } catch (RuntimeException ignored) {
                return -1;
            }
        }

        public static int indexTypeValue(IndexType indexType) {
            if (indexType == null) {
                return -1;
            }
            try {
                return Constants.IndexTypes.getValue(indexType);
            } catch (RuntimeException ignored) {
                return -1;
            }
        }

        public static int vertexFormatType(VertexFormat vertexFormat) {
            if (vertexFormat == null) {
                return -1;
            }
            try {
                return Constants.DefaultVertexFormat.getValue(vertexFormat);
            } catch (RuntimeException ignored) {
                return -1;
            }
        }

        private static boolean isSupportedTargetAndFlags(DrawPacket packet) {
            if (packet.target() == TARGET_SOLID_OPAQUE) {
                return isSupportedSolidOpaqueFlags(packet.flags());
            }
            if (packet.target() == TARGET_NON_OPAQUE_ENTITY) {
                return packet.flags() == FLAG_INDEXED
                    && packet.vertexFormatType() == VERTEX_FORMAT_ENTITY;
            }
            return packet.target() == TARGET_ITEM_ENTITY
                && packet.flags() == lineFlags()
                && packet.drawMode() == DRAW_MODE_LINES
                && packet.vertexFormatType() == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH;
        }

        private static boolean isSupportedSolidOpaqueFlags(int flags) {
            int supported = FLAG_SOLID | FLAG_OPAQUE | FLAG_INDEXED;
            return flags == supported;
        }

        private static boolean isSupportedDrawMode(int drawMode, int vertexFormatType,
            int target) {
            if (vertexFormatType == VERTEX_FORMAT_POSITION_COLOR_NORMAL_LINE_WIDTH) {
                return target == TARGET_ITEM_ENTITY && drawMode == DRAW_MODE_LINES;
            }
            if (vertexFormatType == VERTEX_FORMAT_ENTITY) {
                return (target == TARGET_SOLID_OPAQUE || target == TARGET_NON_OPAQUE_ENTITY)
                    && (drawMode == DRAW_MODE_TRIANGLES || drawMode == DRAW_MODE_QUADS);
            }
            return target == TARGET_SOLID_OPAQUE
                && (drawMode == DRAW_MODE_TRIANGLES || drawMode == DRAW_MODE_QUADS);
        }

        private static boolean isSupportedIndexType(int indexType) {
            return indexType == INDEX_TYPE_SHORT || indexType == INDEX_TYPE_INT;
        }

        private static boolean hasBuffer(int nativeId, ByteBuffer payload) {
            return nativeId >= 0 || payloadAddress(payload) != 0L;
        }

        private static long payloadAddress(ByteBuffer payload) {
            return payload == null || !payload.isDirect() || payload.capacity() <= 0
                ? 0L
                : MemoryUtil.memAddress(payload);
        }

        private static int payloadSize(ByteBuffer payload) {
            return payload == null ? 0 : payload.capacity();
        }

        /*
         * Stable packet fields expected from Java capture:
         * target: TARGET_SOLID_OPAQUE for solid terrain/entity subsets, or
         * TARGET_ITEM_ENTITY for descriptor-correct indexed line replay, or
         * TARGET_NON_OPAQUE_ENTITY for bounded entity translucent/eyes replay.
         * scissor: captured 26.2 PreparedRenderType scissor state, applied before replay.
         * render state: captured packet-local blend/depth/raster/scissor state, applied before
         * every native draw to prevent dynamic-state leakage between accepted packets.
         * buffer/shader ids: ids allocated through BufferProxy and ShaderProxy.
         * drawMode/indexType: BufferProxy primitive/index integer mappings.
         * uniformPtr/uniformSize: direct native memory valid for the duration of the call.
         */
        public record DrawPacket(int target, int flags, boolean scissorEnabled,
            int scissorX, int scissorY, int scissorWidth, int scissorHeight,
            int vertexBufferId, int indexBufferId, int shaderId, int vertexFormatType,
            int drawMode, int indexType, int indexCount, int firstIndex, int vertexOffset,
            int firstInstance, int instanceCount, boolean blendEnabled,
            int srcColorBlendFactor, int srcAlphaBlendFactor, int dstColorBlendFactor,
            int dstAlphaBlendFactor, int colorBlendOp, int alphaBlendOp, int colorWriteMask,
            boolean depthTestEnabled, boolean depthWriteEnabled, int depthCompareOp,
            boolean depthBiasEnabled, float depthBiasSlopeFactor, float depthBiasConstantFactor,
            int cullMode, long uniformPtr, int uniformSize,
            ByteBuffer vertexPayload, ByteBuffer indexPayload) {

        }
    }
}
