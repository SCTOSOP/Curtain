package dev.dubhe.curtain.features.player.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.curtain.Curtain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CurtainClientSkinManager {
    private static final int MAX_PNG_BYTES = 512 * 1024;
    private static final Map<UUID, SkinEntry> SKINS = new HashMap<>();

    public record SkinEntry(ResourceLocation texture, String modelName) {
    }

    public static ResourceLocation getSkin(UUID uuid) {
        SkinEntry entry = SKINS.get(uuid);
        return entry == null ? null : entry.texture();
    }

    public static String getModelName(UUID uuid) {
        SkinEntry entry = SKINS.get(uuid);
        return entry == null ? null : entry.modelName();
    }

    public static void clear(UUID uuid) {
        SKINS.remove(uuid);
        Curtain.LOGGER.info("Cleared client fake player skin for {}", uuid);
    }

    public static void registerFromBytes(UUID uuid, String skinFileName, String model, byte[] pngBytes) {
        if (pngBytes.length > MAX_PNG_BYTES) {
            Curtain.LOGGER.warn("Rejected fake player skin {} for {}: packet is too large ({} bytes)", skinFileName, uuid, pngBytes.length);
            return;
        }
        try {
            NativeImage image = NativeImage.read(pngBytes);
            int width = image.getWidth();
            int height = image.getHeight();
            if (!isAllowedSkinSize(width, height)) {
                Curtain.LOGGER.warn("Rejected fake player skin {} for {}: invalid size {}x{}", skinFileName, uuid, width, height);
                image.close();
                return;
            }
            NativeImage textureImage = processSkinImage(image);
            ResourceLocation texture = new ResourceLocation(Curtain.MODID, "fake_player_skins/" + uuid.toString().replace("-", "_"));
            Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(textureImage));
            String modelName = normalizeModel(model);
            SKINS.put(uuid, new SkinEntry(texture, modelName));
            Curtain.LOGGER.info("Registered client fake player skin {} for {} as {} ({})", skinFileName, uuid, texture, modelName);
        } catch (IOException exception) {
            Curtain.LOGGER.warn("Unable to load fake player skin {} for {}", skinFileName, uuid, exception);
        }
    }

    private static boolean isAllowedSkinSize(int width, int height) {
        return (width == 64 && height == 64) || (width == 64 && height == 32) || (width == 128 && height == 128);
    }

    private static NativeImage processSkinImage(NativeImage image) {
        if (image.getWidth() == 64 && image.getHeight() == 32) {
            NativeImage converted = new NativeImage(64, 64, true);
            converted.copyFrom(image);
            image.close();
            converted.fillRect(0, 32, 64, 32, 0);
            converted.copyRect(4, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(8, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(0, 20, 24, 32, 4, 12, true, false);
            converted.copyRect(4, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(8, 20, 8, 32, 4, 12, true, false);
            converted.copyRect(12, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(44, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(48, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(40, 20, 0, 32, 4, 12, true, false);
            converted.copyRect(44, 20, -8, 32, 4, 12, true, false);
            converted.copyRect(48, 20, -16, 32, 4, 12, true, false);
            converted.copyRect(52, 20, -8, 32, 4, 12, true, false);
            setNoAlpha(converted, 0, 0, 32, 16);
            setNoAlpha(converted, 0, 16, 64, 32);
            setNoAlpha(converted, 16, 48, 48, 64);
            return converted;
        }
        NativeImage textureImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        textureImage.copyFrom(image);
        image.close();
        return textureImage;
    }

    private static void setNoAlpha(NativeImage image, int x, int y, int width, int height) {
        for (int i = x; i < width; ++i) {
            for (int j = y; j < height; ++j) {
                image.setPixelRGBA(i, j, image.getPixelRGBA(i, j) | -16777216);
            }
        }
    }

    private static String normalizeModel(String model) {
        return "slim".equals(model.toLowerCase(Locale.ROOT)) ? "slim" : "default";
    }
}
