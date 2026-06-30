package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;

// Retired in 26.2: tracker capture moved to GPU texture upload/sprite paths.
@Mixin(TextureUtil.class)
public abstract class TextureUtilMixins {
}
