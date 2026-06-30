package com.radiance.mixins.vulkan_render_integration;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.radiance.client.proxy.world.EntityProxy.PARTICLE_COUNTERS;

@Mixin(Particle.class)
public class ParticleMixins implements IParticleExt {

    @Unique
    private String radiance$contentName = null;

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Inject(method = "remove", at = @At("HEAD"))
    private void radiance$removeParticleCounter(CallbackInfo ci) {
        AtomicInteger counter = PARTICLE_COUNTERS.get(((Particle) (Object) this).getClass());
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    @Override
    public double radiance$getX() {
        return x;
    }

    @Override
    public double radiance$getY() {
        return y;
    }

    @Override
    public double radiance$getZ() {
        return z;
    }

    @Override
    public String radiance$getContentName() {
        return radiance$contentName;
    }

    @Override
    public void radiance$setContentName(String contentName) {
        radiance$contentName = contentName;
    }
}
