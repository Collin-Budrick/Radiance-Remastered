package com.radiance.mixins.vulkan_render_integration;

/**
 * Retired 26.2 block-model emission hook.
 *
 * <p>Upstream used the old block model renderer vertex handoff to push tint-based
 * emission into Radiance's PBR vertex stream. Minecraft 26.2's block emission path
 * is restored in {@code SectionBuilderMixins} immediately before each
 * {@code PBRVertexConsumer.putBlockBakedQuad(...)} call, where the custom section
 * mesh writer still has the active quad, block state, tint index, and PBR consumer.
 *
 * <p>Keep this source excluded. Re-adding a {@code ModelBlockRenderer} injection
 * would duplicate or discard the emission lookup instead of writing it into the
 * native section mesh.
 */
public final class BlockModelRendererMixins {

    private BlockModelRendererMixins() {
    }
}
