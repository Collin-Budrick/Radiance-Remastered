package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.util.BlockColorEmissionProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IBlockColorsExt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.IdMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockColors.class)
public class BlockColorsMixins implements IBlockColorsExt {

    @Final
    @Shadow
    private IdMapper<BlockColor> providers;

//    @Redirect(method = "create()Lnet/minecraft/client/color/block/BlockColors;",
//              at = @At(value = "INVOKE",
//                       target = "Lnet/minecraft/client/color/block/BlockColors;registerColorProvider" +
//                           "(Lnet/minecraft/client/color/block/BlockColor;[Lnet/minecraft/block/Block;)V",
//                       ordinal = 7))
//    private static void addEmissionToRedstoneWire(BlockColors blockColors, BlockColor provider, Block[] blocks) {
//        BlockColorEmissionProvider blockColorEmissionProvider = (state, world, pos, tintIndex) -> {
//            int power = state.get(RedstoneWireBlock.POWER);
//            int color = RedstoneWireBlock.getWireColor(power);
//            float emission = 10; // (float) (power * 0.5);
//            return new Tuple<>(color, emission);
//        };
//
//        blockColors.registerColorProvider(blockColorEmissionProvider, Blocks.REDSTONE_WIRE);
//    }

    @Override
    public float radiance$getEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex) {
        BlockColor blockColorProvider = this.providers.get(
            BuiltInRegistries.BLOCK.getRawId(state.getBlock()));
        if (blockColorProvider instanceof BlockColorEmissionProvider blockColorEmissionProvider) {
            return blockColorEmissionProvider.getEmission(state, world, pos, tintIndex);
        } else {
            return 0.0F;
        }
    }
}
