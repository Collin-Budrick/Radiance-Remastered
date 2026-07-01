package com.radiance.mixins.vulkan_render_integration;

/**
 * Retired 26.2 buffer draw hook.
 *
 * <p>The old upstream target {@code BufferRenderer.drawWithGlobalProgram(BuiltBuffer)}
 * is gone. Radiance's 26.2 draw capture lives in
 * {@code PreparedRenderTypeDrawCaptureMixins}, which observes
 * {@code PreparedRenderType.drawFromBuffer(...)} and only cancels vanilla when a
 * registered native replay bridge accepts the packet.
 */
public final class BufferRendererMixins {

    private BufferRendererMixins() {
    }
}
