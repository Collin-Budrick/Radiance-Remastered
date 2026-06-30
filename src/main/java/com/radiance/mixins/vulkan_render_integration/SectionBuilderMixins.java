package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.radiance.client.RendererAvailability;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionBuilderMixins {

    @Shadow
    @Final
    private boolean ambientOcclusion;

    @Shadow
    @Final
    private boolean cutoutLeaves;

    @Shadow
    @Final
    private BlockStateModelSet blockModelSet;

    @Shadow
    @Final
    private FluidStateModelSet fluidModelSet;

    @Shadow
    @Final
    private BlockColors blockColors;

    @Shadow
    private <E extends BlockEntity> void handleBlockEntity(SectionCompiler.Results results,
        E blockEntity) {
        throw new AssertionError();
    }

    @Inject(method =
        "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;"
            + "Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;)"
            + "Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
        at = @At(value = "HEAD"), cancellable = true)
    private void redirectCompile(SectionPos sectionPos, RenderSectionRegion renderRegion,
        VertexSorting vertexSorter, SectionBufferBuilderPack allocatorStorage,
        CallbackInfoReturnable<SectionCompiler.Results> cir) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        SectionCompiler.Results results = new SectionCompiler.Results();
        BlockPos min = sectionPos.origin();
        BlockPos max = min.offset(15, 15, 15);
        VisGraph visGraph = new VisGraph();
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(this.ambientOcclusion, true,
            this.blockColors);
        FluidRenderer fluidRenderer = new FluidRenderer(this.fluidModelSet);
        Map<ChunkSectionLayer, PBRVertexConsumer> builders =
            new EnumMap<>(ChunkSectionLayer.class);
        IBlockColorsExt blockColorsExt = this.blockColors instanceof IBlockColorsExt ext
            ? ext
            : null;
        FluidRenderer.Output fluidOutput = layer -> radiance$beginBuffer(builders,
            allocatorStorage, layer);

        BlockModelLighter.enableCaching();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = renderRegion.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            if (state.isSolidRender()) {
                visGraph.setOpaque(pos);
            }

            if (state.hasBlockEntity()) {
                BlockEntity blockEntity = renderRegion.getBlockEntity(pos);
                if (blockEntity != null) {
                    this.handleBlockEntity(results, blockEntity);
                }
            }

            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                fluidRenderer.tesselate(renderRegion, pos, fluidOutput, state, fluidState);
            }

            if (state.getRenderShape() == RenderShape.MODEL) {
                boolean forceSolid = ModelBlockRenderer.forceOpaque(this.cutoutLeaves, state);
                BlockQuadOutput blockOutput = (x, y, z, quad, instance) -> radiance$putQuad(
                    radiance$beginBuffer(builders, allocatorStorage,
                        forceSolid ? ChunkSectionLayer.SOLID : quad.materialInfo().layer()),
                    x, y, z, quad, instance, blockColorsExt, renderRegion, state, pos);
                blockRenderer.tesselateBlock(blockOutput,
                    SectionPos.sectionRelative(pos.getX()),
                    SectionPos.sectionRelative(pos.getY()),
                    SectionPos.sectionRelative(pos.getZ()),
                    renderRegion, pos, state, this.blockModelSet.get(state),
                    state.getSeed(pos));
            }
        }

        for (Map.Entry<ChunkSectionLayer, PBRVertexConsumer> entry : builders.entrySet()) {
            MeshData meshData = entry.getValue().endNullable();
            if (meshData != null) {
                if (entry.getKey() == ChunkSectionLayer.TRANSLUCENT) {
                    results.transparencyState = meshData.sortQuads(
                        allocatorStorage.buffer(entry.getKey()), vertexSorter);
                }
                results.renderedLayers.put(entry.getKey(), meshData);
            }
        }

        BlockModelLighter.clearCache();
        results.visibilitySet = visGraph.resolve();
        ChunkProxy.uploadCompiledSection(sectionPos, results);
        results.renderedLayers.values().forEach(MeshData::close);
        results.renderedLayers.clear();
        results.transparencyState = null;
        cir.setReturnValue(results);
    }

    @Unique
    private static PBRVertexConsumer radiance$beginBuffer(
        Map<ChunkSectionLayer, PBRVertexConsumer> builders,
        SectionBufferBuilderPack allocatorStorage,
        ChunkSectionLayer layer) {
        PBRVertexConsumer builder = builders.get(layer);
        if (builder == null) {
            ByteBufferBuilder buffer = allocatorStorage.buffer(layer);
            builder = new PBRVertexConsumer(buffer, layer);
            builders.put(layer, builder);
        }

        return builder;
    }

    @Unique
    private static void radiance$putQuad(PBRVertexConsumer builder, float x, float y, float z,
        BakedQuad quad, QuadInstance instance, IBlockColorsExt blockColors,
        BlockAndTintGetter world, BlockState state, BlockPos pos) {
        builder.albedoEmission(radiance$getQuadEmission(blockColors, world, state, pos, quad));
        builder.putBlockBakedQuad(x, y, z, quad, instance);
        builder.albedoEmission(0.0F);
    }

    @Unique
    private static float radiance$getQuadEmission(IBlockColorsExt blockColors,
        BlockAndTintGetter world, BlockState state, BlockPos pos, BakedQuad quad) {
        if (blockColors == null) {
            return 0.0F;
        }

        int tintIndex = quad.materialInfo().tintIndex();
        return tintIndex < 0 ? 0.0F : blockColors.radiance$getEmission(state, world, pos,
            tintIndex);
    }
}
