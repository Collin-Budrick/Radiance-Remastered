package com.radiance.client.proxy.vulkan;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.radiance.client.RadianceClient;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

public class RendererProxy {

    private static final AtomicBoolean RENDER_PASS_PACKET_NATIVE_UNAVAILABLE =
        new AtomicBoolean();
    private static final AtomicBoolean CLOUD_FACES_PACKET_NATIVE_UNAVAILABLE =
        new AtomicBoolean();
    private static final AtomicBoolean SCREENSHOT_NATIVE_OVERLAY_CAPTURE_LOGGED =
        new AtomicBoolean();

    public static native void initFolderPath(String folderPath);

    public static native void initRenderer(String[] glfwLibCandidates, long windowHandle);

    public static void initRenderer(Window window) {
        String mapped = System.mapLibraryName("glfw");
        String[] candidates = {mapped, "libglfw.so.3", "libglfw.3.dylib", "glfw3.dll"};
        RendererProxy.initRenderer(candidates, window.handle());
    }

    public static native int maxSupportedTextureSize();

    public static native void acquireContext();

    public static native void submitCommand();

    public static native void present();

    public static void submitCommandAndPresent() {
        submitCommand();
        present();
    }

    public static native void fuseWorld();

    public static native void postBlur();

    public static native void close();

    public static native void shouldRenderWorld(boolean renderWorld);

    public static native void takeScreenshot(boolean withUI, int width, int height, int channel,
        long pointer);

    private static native boolean submitRenderPassPacketNative(String pipeline,
        String outputTarget,
        boolean scissorEnabled,
        int scissorX,
        int scissorY,
        int scissorWidth,
        int scissorHeight,
        long vertexBufferSize,
        long indexBufferSize,
        int indexTypeOrdinal,
        int baseVertex,
        int firstIndex,
        int indexCount,
        long dynamicTransformLength,
        String[] textureNames,
        int[] textureHandles);

    public static boolean submitRenderPassPacket(String pipeline,
        String outputTarget,
        boolean scissorEnabled,
        int scissorX,
        int scissorY,
        int scissorWidth,
        int scissorHeight,
        long vertexBufferSize,
        long indexBufferSize,
        int indexTypeOrdinal,
        int baseVertex,
        int firstIndex,
        int indexCount,
        long dynamicTransformLength,
        String[] textureNames,
        int[] textureHandles) {
        try {
            return submitRenderPassPacketNative(pipeline, outputTarget, scissorEnabled, scissorX,
                scissorY, scissorWidth, scissorHeight, vertexBufferSize, indexBufferSize,
                indexTypeOrdinal, baseVertex, firstIndex, indexCount, dynamicTransformLength,
                textureNames, textureHandles);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            logNativeUnavailable(RENDER_PASS_PACKET_NATIVE_UNAVAILABLE,
                "Radiance render-pass native packet bridge is unavailable; falling back to vanilla RenderPass draws.",
                e);
            return false;
        }
    }

    private static native boolean submitCloudFacesPacketNative(int relativeCameraOrdinal,
        int cloudStatusOrdinal,
        int faceCount,
        int cellX,
        int cellZ,
        boolean fancy,
        int color,
        byte[] encodedFaces);

    public static boolean submitCloudFacesPacket(int relativeCameraOrdinal,
        int cloudStatusOrdinal,
        int faceCount,
        int cellX,
        int cellZ,
        boolean fancy,
        int color,
        byte[] encodedFaces) {
        try {
            return submitCloudFacesPacketNative(relativeCameraOrdinal, cloudStatusOrdinal,
                faceCount, cellX, cellZ, fancy, color, encodedFaces);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            logNativeUnavailable(CLOUD_FACES_PACKET_NATIVE_UNAVAILABLE,
                "Radiance cloud native packet bridge is unavailable; falling back to vanilla cloud draws.",
                e);
            return false;
        }
    }

    private static void logNativeUnavailable(AtomicBoolean gate, String message, Throwable error) {
        if (gate.compareAndSet(false, true)) {
            RadianceClient.LOGGER.warn(message, error);
        }
    }

    public static NativeImage takeScreenshotWithoutUI() {
        Minecraft mc = Minecraft.getInstance();
        int
            width =
            mc.getWindow()
                .getWidth();
        int
            height =
            mc.getWindow()
                .getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);
        ((INativeImageExt) (Object) nativeImage).radiance$loadFromTextureImageWithoutUI(0, true);
        return nativeImage;
    }

    public static NativeImage takeScreenshotWithNativeOverlay() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        NativeImage nativeImage = new NativeImage(width, height, false);

        if (SCREENSHOT_NATIVE_OVERLAY_CAPTURE_LOGGED.compareAndSet(false, true)) {
            RadianceClient.LOGGER.info(
                "Radiance screenshot capture using native overlay color target");
        }

        takeScreenshot(true, width, height, nativeImage.format().components(),
            nativeImage.getPointer());
        if (nativeImage.format().hasAlpha()) {
            for (int y = 0; y < nativeImage.getHeight(); y++) {
                for (int x = 0; x < nativeImage.getWidth(); x++) {
                    nativeImage.setPixel(x, y,
                        nativeImage.getPixel(x, y) | 255 << nativeImage.format().alphaOffset());
                }
            }
        }
        return nativeImage;
    }
}
