package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.texture.AuxiliaryTextures;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.DiscardableLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import net.minecraft.client.renderer.texture.atlas.sources.PalettedPermutations;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PalettedPermutations.class)
public class PalettedPermutationsAtlasSourceMixins {

    @Redirect(method = "run(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$Output;add(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/atlas/SpriteSource$DiscardableLoader;)V"))
    public void cancelPBRLoad(Output instance, Identifier identifier,
        DiscardableLoader spriteRegion, ResourceManager resourceManager) {
        if (AuxiliaryTextures.shouldSkipAtlasSprite(resourceManager, identifier)) {
            return;
        }
        instance.add(identifier, spriteRegion);
    }
}
