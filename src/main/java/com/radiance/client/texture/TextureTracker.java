package com.radiance.client.texture;

import com.radiance.client.RendererAvailability;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class TextureTracker {

    public static Map<Identifier, Integer> textureID2GLID = new ConcurrentHashMap<>();
    public static Map<Integer, Texture> GLID2Texture = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2SpecularGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2NormalGLID = new ConcurrentHashMap<>();
    public static Map<Integer, Integer> GLID2FlagGLID = new ConcurrentHashMap<>();
    public static Map<GpuTexture, Integer> GPU_TEXTURE2GLID = new ConcurrentHashMap<>();
    public static Map<GpuTexture, Identifier> GPU_TEXTURE2IDENTIFIER = new ConcurrentHashMap<>();
    public static Map<GpuTexture, Set<Identifier>> GPU_TEXTURE2IDENTIFIERS =
        new ConcurrentHashMap<>();

    public static int registerGpuTexture(GpuTexture gpuTexture) {
        return registerGpuTexture(gpuTexture, false);
    }

    public static int registerLightmapTexture(GpuTexture gpuTexture) {
        return registerGpuTexture(gpuTexture, true);
    }

    public static int registerGuiTexture(GpuTexture gpuTexture) {
        return registerGpuTexture(gpuTexture, true);
    }

    private static int registerGpuTexture(GpuTexture gpuTexture, boolean allowSmallTexture) {
        if (!shouldMirrorTextures()) {
            return 0;
        }

        Integer existing = GPU_TEXTURE2GLID.get(gpuTexture);
        if (existing != null) {
            return existing;
        }

        Texture trackerTexture;
        try {
            trackerTexture = new Texture(gpuTexture);
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
        if (!allowSmallTexture && trackerTexture.width() < 64 && trackerTexture.height() < 64) {
            return 0;
        }

        int id = TextureProxy.generateTextureId();
        GLID2Texture.put(id, trackerTexture);
        TextureProxy.prepareImage(id, trackerTexture.maxLayer(), trackerTexture.width(),
            trackerTexture.height(), trackerTexture.format());
        GPU_TEXTURE2GLID.put(gpuTexture, id);
        Identifier identifier = GPU_TEXTURE2IDENTIFIER.get(gpuTexture);
        if (identifier != null) {
            textureID2GLID.put(identifier, id);
        }
        Set<Identifier> identifiers = GPU_TEXTURE2IDENTIFIERS.get(gpuTexture);
        if (identifiers != null) {
            for (Identifier trackedIdentifier : identifiers) {
                textureID2GLID.put(trackedIdentifier, id);
            }
        }
        return id;
    }

    public static int getOrRegisterGpuTexture(GpuTexture gpuTexture) {
        Integer id = GPU_TEXTURE2GLID.get(gpuTexture);
        return id != null ? id : registerGpuTexture(gpuTexture);
    }

    public static int getOrRegisterGuiTexture(GpuTexture gpuTexture) {
        Integer id = GPU_TEXTURE2GLID.get(gpuTexture);
        return id != null ? id : registerGuiTexture(gpuTexture);
    }

    public static void registerTextureIdentifier(Identifier identifier, GpuTexture gpuTexture) {
        rememberTextureIdentifier(identifier, gpuTexture);
        if (identifier != null && gpuTexture != null && shouldMirrorTextures()) {
            int id = shouldAllowSmallTexture(identifier, gpuTexture)
                ? getOrRegisterGuiTexture(gpuTexture)
                : getOrRegisterGpuTexture(gpuTexture);
            if (id != 0) {
                textureID2GLID.put(identifier, id);
            }
        }
    }

    public static void rememberTextureIdentifier(Identifier identifier, GpuTexture gpuTexture) {
        if (identifier != null && gpuTexture != null) {
            GPU_TEXTURE2IDENTIFIER.put(gpuTexture, identifier);
            GPU_TEXTURE2IDENTIFIERS.computeIfAbsent(gpuTexture,
                ignored -> ConcurrentHashMap.newKeySet()).add(identifier);
            Integer existing = GPU_TEXTURE2GLID.get(gpuTexture);
            if (existing != null && existing != 0) {
                textureID2GLID.put(identifier, existing);
            }
        }
    }

    public static boolean shouldMirrorTextures() {
        return RendererAvailability.isRendererLifecycleActive()
            && Minecraft.getInstance() != null;
    }

    public static boolean shouldAllowSmallTexture(Identifier identifier, GpuTexture gpuTexture) {
        if (identifier != null) {
            String path = identifier.getPath();
            if (path.contains("/gui/")
                || path.contains("textures/gui")
                || path.contains("textures/font")
                || path.contains("font/")
                || path.contains("hud/")) {
                return true;
            }
        }

        if (gpuTexture != null && gpuTexture.getLabel() != null) {
            String label = gpuTexture.getLabel().toLowerCase(java.util.Locale.ROOT);
            return label.contains("gui") || label.contains("font") || label.contains("hud");
        }
        return false;
    }

    public record Texture(int width, int height, int channel, VulkanConstants.VkFormat format,
                          int maxLayer) {

        public Texture {
            if (width <= 0 || height <= 0 || channel <= 0 || maxLayer < 0) {
                throw new IllegalArgumentException(
                    "Invalid texture width, height, channel, or maxLayer: " + width + ", " + height
                        + ", " + channel + ", " + maxLayer);
            }
        }

        public Texture(int width, int height, NativeImage.Format format, int maxLayer) {
            this(width, height, getChannel(format), getFormat(format), maxLayer);
        }

        public Texture(GpuTexture texture) {
            this(texture.getWidth(0), texture.getHeight(0), texture.getFormat().componentCount(),
                getFormat(texture.getFormat()), Math.max(1, texture.getMipLevels()));
        }

        private static int getChannel(NativeImage.Format internalFormat) {
            return switch (internalFormat) {
                case RGBA -> 4;
                case RGB -> 3;
                case LUMINANCE_ALPHA -> 2;
                case LUMINANCE -> 1;
                default -> throw new IllegalArgumentException(
                    "Unknown internal format: " + internalFormat);
            };
        }

        private static VulkanConstants.VkFormat getFormat(
            NativeImage.Format internalFormat) {
            return switch (internalFormat) {
                case RGBA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                case RGB -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8_UNORM;
                case LUMINANCE_ALPHA -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case LUMINANCE -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
            };
        }

        private static VulkanConstants.VkFormat getFormat(GpuFormat format) {
            return switch (format) {
                case R8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
                case RG8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                case RGBA8_UNORM -> VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                default -> throw new IllegalArgumentException("Unsupported GPU texture format: "
                    + format);
            };
        }
    }
}
