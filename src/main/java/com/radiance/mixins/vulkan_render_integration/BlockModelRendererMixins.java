package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class BlockModelRendererMixins {

    @Final
    @Shadow
    private BlockColors blockColors;

    @Inject(method = "putQuadWithTint", at = @At("HEAD"))
    private void radiance$captureQuadEmission(BlockQuadOutput output, float x, float y, float z,
        BlockAndTintGetter world, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
        int tintIndex = quad.materialInfo().tintIndex();
        if (tintIndex != -1 && this.blockColors instanceof IBlockColorsExt blockColorsExt) {
            blockColorsExt.radiance$getEmission(state, world, pos, tintIndex);
        }
    }
}
