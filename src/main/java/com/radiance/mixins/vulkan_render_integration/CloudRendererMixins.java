package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.radiance.client.proxy.world.CloudProxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixins {

    @Shadow
    private CloudRenderer.TextureData texture;

    @Shadow
    private int quadCount;

    @Unique
    private ByteBuffer radiance$encodedCloudFaces;

    @Unique
    private int radiance$encodedCloudFaceBytes;

    @Inject(method =
        "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CloudRenderer;buildMesh(Lnet/minecraft/client/renderer/CloudRenderer$RelativeCameraPos;Ljava/nio/ByteBuffer;IIZI)V",
            shift = At.Shift.AFTER))
    private void radiance$captureEncodedCloudFaces(int color, CloudStatus cloudStatus,
        float bottomY, int range, Vec3 cameraPosition, long gameTime, float partialTicks,
        CallbackInfo ci, @Local GpuBufferSlice.MappedView view) {
        ByteBuffer data = view.data();
        int faceBytes = data.position();
        if (faceBytes <= 0) {
            this.radiance$encodedCloudFaceBytes = 0;
            return;
        }

        ByteBuffer source = data.duplicate();
        source.flip();
        this.radiance$ensureEncodedCloudCapacity(faceBytes);
        this.radiance$encodedCloudFaces.clear();
        this.radiance$encodedCloudFaces.put(source);
        this.radiance$encodedCloudFaces.flip();
        this.radiance$encodedCloudFaceBytes = faceBytes;
    }

    @Inject(method =
        "render(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createCommandEncoder()Lcom/mojang/blaze3d/systems/CommandEncoder;"),
        cancellable = true)
    private void radiance$submitEncodedCloudFaces(int color, CloudStatus cloudStatus,
        float bottomY, int range, Vec3 cameraPosition, long gameTime, float partialTicks,
        CallbackInfo ci) {
        if (this.radiance$encodedCloudFaceBytes <= 0 || this.radiance$encodedCloudFaces == null
            || this.texture == null || this.quadCount <= 0) {
            return;
        }

        CloudProxy.SubmitResult submitResult = CloudProxy.submitEncodedFaces(
            this.radiance$buildCloudPacket(color, cloudStatus, bottomY, range, cameraPosition,
                gameTime, partialTicks));
        if (submitResult.shouldCancelVanilla()) {
            ci.cancel();
        }
    }

    @Unique
    private void radiance$ensureEncodedCloudCapacity(int faceBytes) {
        if (this.radiance$encodedCloudFaces == null
            || this.radiance$encodedCloudFaces.capacity() < faceBytes) {
            this.radiance$encodedCloudFaces =
                ByteBuffer.allocateDirect(faceBytes).order(ByteOrder.nativeOrder());
        }
    }

    @Unique
    private CloudProxy.EncodedCloudPacket radiance$buildCloudPacket(int color,
        CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition, long gameTime,
        float partialTicks) {
        int horizontalRange = range * 16;
        int radiusCells = Mth.ceil((float) horizontalRange / 12.0F);
        float cloudBaseRelativeY = bottomY - (float) cameraPosition.y;
        float cloudTopRelativeY = cloudBaseRelativeY + 4.0F;
        int relativeCameraPosition = radiance$relativeCameraPosition(cloudBaseRelativeY,
            cloudTopRelativeY);
        boolean fancy = cloudStatus == CloudStatus.FANCY;

        float scroll = (float) (gameTime % ((long) this.texture.width() * 400L)) + partialTicks;
        double wrappedCloudX = cameraPosition.x + (double) (scroll * 0.030000001F);
        double wrappedCloudZ = cameraPosition.z + 3.9600000381469727D;
        double cloudWidthBlocks = (double) this.texture.width() * 12.0D;
        double cloudHeightBlocks = (double) this.texture.height() * 12.0D;
        wrappedCloudX -= (double) Mth.floor(wrappedCloudX / cloudWidthBlocks) * cloudWidthBlocks;
        wrappedCloudZ -= (double) Mth.floor(wrappedCloudZ / cloudHeightBlocks)
            * cloudHeightBlocks;

        int cellX = Mth.floor(wrappedCloudX / 12.0D);
        int cellZ = Mth.floor(wrappedCloudZ / 12.0D);
        float cellOffsetX = (float) (wrappedCloudX - (double) ((float) cellX * 12.0F));
        float cellOffsetZ = (float) (wrappedCloudZ - (double) ((float) cellZ * 12.0F));

        return new CloudProxy.EncodedCloudPacket(color, cloudStatus == null ? -1
            : cloudStatus.ordinal(), fancy, relativeCameraPosition, this.texture.width(),
            this.texture.height(), cellX, cellZ, radiusCells, this.quadCount,
            cloudBaseRelativeY, cloudTopRelativeY, cellOffsetX, cellOffsetZ, cameraPosition.x,
            cameraPosition.y, cameraPosition.z, bottomY, gameTime, partialTicks,
            this.radiance$encodedCloudFaces.duplicate(), this.radiance$encodedCloudFaceBytes);
    }

    @Unique
    private static int radiance$relativeCameraPosition(float cloudBaseRelativeY,
        float cloudTopRelativeY) {
        if (cloudTopRelativeY < 0.0F) {
            return 0;
        }
        if (cloudBaseRelativeY > 0.0F) {
            return 2;
        }
        return 1;
    }
}
