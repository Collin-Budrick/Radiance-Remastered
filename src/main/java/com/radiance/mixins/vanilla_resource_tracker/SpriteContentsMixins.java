package com.radiance.mixins.vanilla_resource_tracker;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixins implements ISpriteContentsExt {

    @Unique
    private int targetID;

    @Shadow
    private NativeImage[] byMipLevel;

    @Shadow
    public abstract Identifier name();

    @Override
    public int radiance$getTargetID() {
        return targetID;
    }

    @Override
    public void radiance$setTargetID(int targetID) {
        this.targetID = targetID;
    }

    @Inject(method = "uploadFirstFrame(Lcom/mojang/blaze3d/textures/GpuTexture;I)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;writeToTexture(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/platform/NativeImage;IIII)V"))
    public void setImageTargetIDBeforeUpload(GpuTexture texture, int mipLevel, CallbackInfo ci) {
        Identifier identifier = name();
        TextureTracker.rememberTextureIdentifier(identifier, texture);
        int id = TextureTracker.getOrRegisterGuiTexture(texture);
        radiance$setTargetID(id);

        if (byMipLevel != null && mipLevel >= 0 && mipLevel < byMipLevel.length
            && byMipLevel[mipLevel] != null) {
            INativeImageExt imageExt = (INativeImageExt) (Object) byMipLevel[mipLevel];
            imageExt.radiance$setIdentifier(identifier);
            imageExt.radiance$setTargetID(id);
        }
    }
}
