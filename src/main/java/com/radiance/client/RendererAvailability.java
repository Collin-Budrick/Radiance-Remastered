package com.radiance.client;

import java.nio.file.Path;
import java.util.Locale;

public final class RendererAvailability {

    public static final String REQUIRED_PROPERTY = "radiance.renderer.required";
    public static final String REQUIRED_ENV = "RADIANCE_RENDERER_REQUIRED";
    private static volatile boolean nativeRendererLoaded;
    private static volatile boolean shaderResourcesStaged;
    private static volatile boolean rendererInitialized;

    private RendererAvailability() {
    }

    public static boolean isRendererRequired() {
        String propertyValue = System.getProperty(REQUIRED_PROPERTY);
        if (propertyValue != null) {
            return parseBoolean(propertyValue);
        }

        return parseBoolean(System.getenv(REQUIRED_ENV));
    }

    public static boolean hasPackagedRendererResources() {
        return hasPackagedNativeLibrary() && hasResource("/shaders") && hasResource("/modules");
    }

    public static boolean shouldUsePackagedRenderer() {
        return isRendererRequired() && hasPackagedRendererResources();
    }

    public static boolean shouldOwnRendererLifecycle() {
        return shouldUsePackagedRenderer();
    }

    public static boolean hasPackagedNativeLibrary() {
        String nativeLibraryResourceName = nativeLibraryResourceName();
        return nativeLibraryResourceName != null && hasResource("/" + nativeLibraryResourceName);
    }

    public static String nativeLibraryResourceName() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("windows")) {
            return "core.dll";
        }
        if (osName.contains("linux")) {
            return "libcore.so";
        }
        return null;
    }

    public static void ensureRendererAvailableIfRequired() {
        if (!isRendererRequired() || shouldUsePackagedRenderer()) {
            return;
        }

        throw new IllegalStateException(missingRendererMessage());
    }

    public static String missingRendererMessage() {
        String nativeLibraryResourceName = nativeLibraryResourceName();
        String expectedNative = nativeLibraryResourceName == null
            ? "a supported OS native library"
            : nativeLibraryResourceName;
        return "Radiance native renderer was requested, but packaged renderer resources are missing. "
            + "Expected classpath resources /" + expectedNative + ", /shaders, and /modules. "
            + "Build/package the MCVR native renderer resources with Radiance, or unset -D"
            + REQUIRED_PROPERTY + "=true / " + REQUIRED_ENV + "=true to start without the native renderer.";
    }

    public static void markNativeRendererLoaded(Path path) {
        nativeRendererLoaded = true;
        RadianceClient.LOGGER.info("Radiance lifecycle marker: native renderer loaded from {}",
            path == null ? "<unknown>" : path.toAbsolutePath());
    }

    public static void markShaderResourcesStaged(Path path, boolean staged) {
        shaderResourcesStaged = staged;
        if (staged) {
            RadianceClient.LOGGER.info("Radiance lifecycle marker: shader resources staged at {}",
                path == null ? "<unknown>" : path.toAbsolutePath());
        } else {
            RadianceClient.LOGGER.warn("Radiance lifecycle marker: shader resources are not staged at {}",
                path == null ? "<unknown>" : path.toAbsolutePath());
        }
    }

    public static void markRendererInitialized() {
        rendererInitialized = true;
        RadianceClient.LOGGER.info("Radiance lifecycle marker: native renderer initialized");
    }

    public static void markRendererStopped() {
        rendererInitialized = false;
        RadianceClient.LOGGER.info("Radiance lifecycle marker: native renderer stopped");
    }

    public static boolean isNativeRendererLoaded() {
        return nativeRendererLoaded;
    }

    public static boolean areShaderResourcesStaged() {
        return shaderResourcesStaged;
    }

    public static boolean isRendererInitialized() {
        return rendererInitialized;
    }

    public static boolean canInitializeRendererLifecycle() {
        return shouldOwnRendererLifecycle() && nativeRendererLoaded && shaderResourcesStaged;
    }

    public static boolean isRendererLifecycleActive() {
        return rendererInitialized && canInitializeRendererLifecycle();
    }

    private static boolean hasResource(String path) {
        return RendererAvailability.class.getResource(path) != null;
    }

    private static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
            || normalized.equals("1")
            || normalized.equals("yes")
            || normalized.equals("on");
    }
}
