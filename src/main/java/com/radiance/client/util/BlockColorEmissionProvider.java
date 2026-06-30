package com.radiance.client.util;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

public interface BlockColorEmissionProvider {

    ColorEmission getColorEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex);

    default int getColor(BlockState state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos,
        int tintIndex) {
        return getColorEmission(state, world, pos, tintIndex).color();
    }

    default float getEmission(BlockState state, @Nullable BlockAndTintGetter world,
        @Nullable BlockPos pos, int tintIndex) {
        return getColorEmission(state, world, pos, tintIndex).emission();
    }

    record ColorEmission(int color, float emission) {
    }
}
