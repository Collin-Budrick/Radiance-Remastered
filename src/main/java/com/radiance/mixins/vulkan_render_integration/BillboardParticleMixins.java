package com.radiance.mixins.vulkan_render_integration;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WhiteAshParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.util.ARGB;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleQuadParticle.class)
public abstract class BillboardParticleMixins {

    @Shadow
    protected float rCol;

    @Shadow
    protected float gCol;

    @Shadow
    protected float bCol;

    @Shadow
    protected float alpha;

    @Inject(method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
        at = @At(value = "HEAD"),
        cancellable = true)
    private void resizeParticle(QuadParticleRenderState state,
        Quaternionf quaternionf,
        float x,
        float y,
        float z,
        float tickProgress,
        CallbackInfo ci) {
        if (((SingleQuadParticle) (Object) this) instanceof WhiteAshParticle) {
            float size = this.getQuadSize(tickProgress) * (1.0F / 8.0F);
            state.add(this.getLayer(),
                x,
                y,
                z,
                quaternionf.x,
                quaternionf.y,
                quaternionf.z,
                quaternionf.w,
                size,
                this.getU0(),
                this.getU1(),
                this.getV0(),
                this.getV1(),
                ARGB.colorFromFloat(this.alpha, this.rCol, this.gCol, this.bCol),
                0);
            ci.cancel();
        }
    }

    @Shadow
    public abstract float getQuadSize(float tickProgress);

    @Shadow
    protected abstract float getU0();

    @Shadow
    protected abstract float getU1();

    @Shadow
    protected abstract float getV0();

    @Shadow
    protected abstract float getV1();

    @Shadow
    protected abstract SingleQuadParticle.Layer getLayer();
}
