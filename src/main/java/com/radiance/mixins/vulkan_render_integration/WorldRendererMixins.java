package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.radiance.client.RadianceClient;
import com.radiance.client.RendererAvailability;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.client.texture.TextureResourceBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.TextureUploadReplay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixins {

    @Shadow
    private ViewArea viewArea;

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Unique
    private static boolean radiance$loggedWorldUniformBridge = false;
    @Unique
    private static boolean radiance$loggedSkyUniformBridge = false;
    @Unique
    private static boolean radiance$loggedCameraBridge = false;
    @Unique
    private static boolean radiance$loggedMatrixBridge = false;
    @Unique
    private static boolean radiance$loggedProjectionFallback = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void radiance$beginWorldRender(GraphicsResourceAllocator allocator,
        DeltaTracker tickCounter, boolean renderBlockOutline, CameraRenderState camera,
        Matrix4fc frustumMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor,
        boolean panoramicMode, CallbackInfo ci) {
        if (!RendererAvailability.isRendererLifecycleActive()) {
            return;
        }

        if (camera.pos != null) {
            PlayerProxy.setCameraPos(camera.pos);
        }
        radiance$logCameraBridge(camera);
        ChunkProxy.setStorage(this.viewArea);
        radiance$updateWorldUniform(camera);
        radiance$updateSkyUniform(this.levelRenderState == null ? null : this.levelRenderState.skyRenderState,
            camera, tickCounter);
        TextureUploadReplay.replayAll();
        BufferProxy.updateMapping();
        ChunkProxy.rebuild(Minecraft.getInstance().gameRenderer.mainCamera());
    }

    @Unique
    private static void radiance$logCameraBridge(CameraRenderState camera) {
        if (radiance$loggedCameraBridge || !RendererAvailability.isRendererLifecycleActive()
            || camera == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        var mainCamera = minecraft.gameRenderer == null ? null : minecraft.gameRenderer.mainCamera();
        RadianceClient.LOGGER.info(
            "Radiance camera bridge: statePos={}, stateBlock={}, mainCameraPos={}, mainCameraBlock={}, xRot={}, yRot={}",
            camera.pos, camera.blockPos,
            mainCamera == null ? null : mainCamera.position(),
            mainCamera == null ? null : mainCamera.blockPosition(),
            camera.xRot, camera.yRot);
        radiance$loggedCameraBridge = true;
    }

    @Unique
    private static void radiance$updateWorldUniform(CameraRenderState camera) {
        if (!RendererAvailability.isRendererLifecycleActive() || camera == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null
            || minecraft.gameRenderer.mainCamera() == null) {
            return;
        }

        Matrix4f viewMatrix = camera.viewRotationMatrix == null
            ? new Matrix4f()
            : new Matrix4f(camera.viewRotationMatrix);
        Matrix4f effectedViewMatrix = new Matrix4f(viewMatrix);
        Matrix4f projectionMatrix = radiance$projectionMatrix(camera, minecraft);
        radiance$logMatrixBridge(camera, effectedViewMatrix, projectionMatrix);

        BufferProxy.updateWorldUniform(minecraft.gameRenderer.mainCamera(), viewMatrix,
            effectedViewMatrix, projectionMatrix, radiance$getOverlayTextureId(minecraft),
            camera.fogData, minecraft.level,
            TextureResourceBridge.optionalTextureId(TextureResourceBridge.END_SKY_TEXTURE),
            TextureResourceBridge.optionalTextureId(TextureResourceBridge.END_PORTAL_TEXTURE),
            radiance$getLevelLightmapTextureId(minecraft));

        if (!radiance$loggedWorldUniformBridge) {
            radiance$loggedWorldUniformBridge = true;
            RadianceClient.LOGGER.info(
                "Radiance world uniform bridge: using 26.2 CameraRenderState projection/view/fog path");
        }
    }

    @Unique
    private static Matrix4f radiance$projectionMatrix(CameraRenderState camera, Minecraft minecraft) {
        Matrix4f projectionMatrix = camera.projectionMatrix == null
            ? new Matrix4f()
            : new Matrix4f(camera.projectionMatrix);
        if (radiance$isUsableProjection(projectionMatrix)) {
            return projectionMatrix;
        }

        int width = minecraft.getWindow() == null ? 0 : minecraft.getWindow().getWidth();
        int height = minecraft.getWindow() == null ? 0 : minecraft.getWindow().getHeight();
        float aspect = width > 0 && height > 0 ? (float) width / (float) height : 16.0F / 9.0F;
        float fov = Float.isFinite(camera.hudFov) && camera.hudFov >= 30.0F
            && camera.hudFov <= 120.0F ? camera.hudFov : 70.0F;
        float far = Float.isFinite(camera.depthFar) && camera.depthFar >= 16.0F
            ? camera.depthFar : 1000.0F;
        Matrix4f fallback = new Matrix4f().setPerspective((float) Math.toRadians(fov), aspect,
            0.05F, far);
        Matrix4f radianceProjection = radiance$isNativeClipMapForced()
            ? fallback
            : radiance$mapGlToVulkanClip(fallback);

        if (!radiance$loggedProjectionFallback) {
            radiance$loggedProjectionFallback = true;
            RadianceClient.LOGGER.info(
                "Radiance world projection bridge: replaced unusable 26.2 projection with fallback fov={}, aspect={}, near=0.05, far={}, vulkanClipMapped={}",
                fov, aspect, far, !radiance$isNativeClipMapForced());
        }
        return radianceProjection;
    }

    @Unique
    private static Matrix4f radiance$mapGlToVulkanClip(Matrix4f projection) {
        Matrix4f map = new Matrix4f();
        map.m11(-1.0F);
        map.m22(0.5F);
        map.m32(0.5F);
        return map.mul(projection, new Matrix4f());
    }

    @Unique
    private static boolean radiance$isNativeClipMapForced() {
        return System.getenv("RADIANCE_FORCE_PROJECTION_CLIP_MAP") != null;
    }

    @Unique
    private static boolean radiance$isUsableProjection(Matrix4f matrix) {
        float xScale = Math.abs(matrix.m00());
        float yScale = Math.abs(matrix.m11());
        return Float.isFinite(xScale) && Float.isFinite(yScale)
            && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
            && Float.isFinite(matrix.m32()) && xScale > 0.05F && yScale > 0.05F
            && xScale < 8.0F && yScale < 8.0F;
    }

    @Unique
    private static void radiance$logMatrixBridge(CameraRenderState camera, Matrix4f viewMatrix,
        Matrix4f projectionMatrix) {
        if (radiance$loggedMatrixBridge) {
            return;
        }

        Matrix4f inverseView = new Matrix4f(viewMatrix).invert();
        Matrix4f inverseProjection = new Matrix4f(projectionMatrix).invert();
        Vector4f centerNear = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
        inverseProjection.transform(centerNear);
        if (centerNear.w() != 0.0F) {
            centerNear.div(centerNear.w());
        }

        Vector3f rayOrigin = inverseView.transformPosition(new Vector3f(0.0F, 0.0F, 0.0F));
        Vector3f centerRay = inverseView.transformDirection(
            new Vector3f(centerNear.x(), centerNear.y(), centerNear.z()));
        if (centerRay.lengthSquared() > 0.0F) {
            centerRay.normalize();
        }

        RadianceClient.LOGGER.info(
            "Radiance world matrix bridge: cameraPos={}, shaderOrigin={}, centerRay={}, viewTranslation=({}, {}, {}), projection00={}, projection11={}",
            camera.pos, rayOrigin, centerRay, viewMatrix.m30(), viewMatrix.m31(),
            viewMatrix.m32(), projectionMatrix.m00(), projectionMatrix.m11());
        radiance$loggedMatrixBridge = true;
    }

    @Unique
    private static int radiance$getOverlayTextureId(Minecraft minecraft) {
        GpuTextureView view = minecraft.gameRenderer.overlayTexture().getTextureView();
        if (view == null || view.texture() == null || view.isClosed()) {
            return 0;
        }
        return TextureTracker.registerLightmapTexture(view.texture());
    }

    @Unique
    private static int radiance$getLevelLightmapTextureId(Minecraft minecraft) {
        GpuTextureView view = minecraft.gameRenderer.levelLightmap();
        if (view == null || view.texture() == null || view.isClosed()) {
            return 0;
        }
        return TextureTracker.registerLightmapTexture(view.texture());
    }

    @Unique
    private static void radiance$updateSkyUniform(SkyRenderState sky, CameraRenderState camera,
        DeltaTracker tickCounter) {
        if (!RendererAvailability.isRendererLifecycleActive() || sky == null
            || sky.skybox == DimensionType.Skybox.NONE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        int skyColor = sky.skyColor;
        int horizonColor = sky.sunriseAndSunsetColor;
        boolean sunRisingOrSetting = ARGB.alphaFloat(horizonColor) > 0.001F;
        float rainGradient = Math.max(0.0F, Math.min(1.0F, 1.0F - sky.rainBrightness));
        int submersionType = radiance$legacySubmersionType(camera);
        int moonPhase = sky.moonPhase == null ? 0 : sky.moonPhase.index();

        BufferProxy.updateSkyUniform(ARGB.redFloat(skyColor), ARGB.greenFloat(skyColor),
            ARGB.blueFloat(skyColor), ARGB.redFloat(horizonColor), ARGB.greenFloat(horizonColor),
            ARGB.blueFloat(horizonColor), ARGB.alphaFloat(horizonColor),
            radiance$sunDirection(sky.sunAngle), sky.skybox.ordinal(), sunRisingOrSetting,
            minecraft.level.isDarkOutside(), false, submersionType, moonPhase, rainGradient,
            TextureResourceBridge.textureId(TextureResourceBridge.SUN_TEXTURE),
            TextureResourceBridge.moonAtlasTextureId());

        if (!radiance$loggedSkyUniformBridge) {
            radiance$loggedSkyUniformBridge = true;
            RadianceClient.LOGGER.info(
                "Radiance sky uniform bridge: using 26.2 SkyRenderState sun/moon/skybox path; skyType={}, fogType={}, legacySubmersionType={}, rainGradient={}, skyDark={}, sunDirection={}",
                sky.skybox.ordinal(), camera == null ? null : camera.fogType, submersionType,
                rainGradient, minecraft.level.isDarkOutside(), radiance$sunDirection(sky.sunAngle));
        }
    }

    @Unique
    private static int radiance$legacySubmersionType(CameraRenderState camera) {
        if (camera == null || camera.fogType == null) {
            return 3;
        }

        FogType fogType = camera.fogType;
        if (fogType == FogType.LAVA) {
            return 0;
        }
        if (fogType == FogType.WATER) {
            return 1;
        }
        if (fogType == FogType.POWDER_SNOW) {
            return 2;
        }
        return 3;
    }

    @Unique
    private static Vector3f radiance$sunDirection(float sunAngle) {
        return new Vector3f(0.0F, 1.0F, 0.0F)
            .rotateX(sunAngle)
            .rotateY((float) Math.toRadians(-90.0F))
            .normalize();
    }
}
