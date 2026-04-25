package dev.dubhe.curtain.features.player.helpers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.dubhe.curtain.Curtain;
import dev.dubhe.curtain.features.player.patches.EntityPlayerMPFake;
import dev.dubhe.curtain.network.CurtainNetwork;
import dev.dubhe.curtain.network.FakePlayerSkinSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class FakePlayerSkinManager {
    private static final Gson GSON = new Gson();
    private static final String CONFIG_FILE = "fake_player_skins.json";
    private static final int MAX_PNG_BYTES = FakePlayerSkinSyncPacket.MAX_PNG_BYTES;
    private static final Map<String, SkinSetting> SKINS = new HashMap<>();
    private static MinecraftServer loadedServer;

    public record SkinSetting(String skin, String model) {
    }

    public static synchronized boolean hasSkin(MinecraftServer server, String playerName) {
        ensureLoaded(server);
        return SKINS.containsKey(playerName.toLowerCase(Locale.ROOT));
    }

    public static synchronized void setSkin(MinecraftServer server, String playerName, String skin, String model) throws IOException {
        ensureLoaded(server);
        validateSkinName(skin);
        model = normalizeModel(model);
        Path skinPath = getSkinPath(server, skin);
        if (!Files.isRegularFile(skinPath)) {
            throw new IOException("Skin file does not exist: " + skinPath);
        }
        long size = Files.size(skinPath);
        if (size > MAX_PNG_BYTES) {
            throw new IOException("Skin file is too large: " + size + " bytes (max " + MAX_PNG_BYTES + ")");
        }
        SKINS.put(playerName.toLowerCase(Locale.ROOT), new SkinSetting(skin, model));
        save(server);
    }

    public static synchronized void clearSkin(MinecraftServer server, String playerName) throws IOException {
        ensureLoaded(server);
        SKINS.remove(playerName.toLowerCase(Locale.ROOT));
        save(server);
    }

    public static synchronized Collection<String> listSkins(MinecraftServer server) {
        ensureLoaded(server);
        Path skinDir = getSkinDirectory(server);
        if (!Files.isDirectory(skinDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(skinDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .toList();
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    public static synchronized SkinSetting getSkinSetting(MinecraftServer server, String playerName) {
        ensureLoaded(server);
        return SKINS.get(playerName.toLowerCase(Locale.ROOT));
    }

    public static synchronized void syncToAll(MinecraftServer server, ServerPlayer fakePlayer) {
        FakePlayerSkinSyncPacket packet = createSyncPacket(server, fakePlayer);
        if (packet == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, packet);
        }
    }

    public static synchronized void syncClearToAll(MinecraftServer server, ServerPlayer fakePlayer) {
        FakePlayerSkinSyncPacket packet = new FakePlayerSkinSyncPacket(
                fakePlayer.getUUID(),
                fakePlayer.getGameProfile().getName(),
                "",
                "default",
                new byte[0],
                true
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, packet);
        }
    }

    public static synchronized void syncAllToPlayer(ServerPlayer receiver) {
        MinecraftServer server = receiver.getServer();
        if (server == null) return;
        ensureLoaded(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof EntityPlayerMPFake) {
                FakePlayerSkinSyncPacket packet = createSyncPacket(server, player);
                if (packet != null) {
                    sendToPlayer(receiver, packet);
                }
            }
        }
    }

    private static FakePlayerSkinSyncPacket createSyncPacket(MinecraftServer server, ServerPlayer fakePlayer) {
        SkinSetting setting = getSkinSetting(server, fakePlayer.getGameProfile().getName());
        if (setting == null) return null;
        try {
            byte[] pngBytes = readSkinBytes(server, setting.skin());
            Curtain.LOGGER.info("Syncing fake player skin {} ({}) for {} to clients", setting.skin(), setting.model(), fakePlayer.getGameProfile().getName());
            return new FakePlayerSkinSyncPacket(
                    fakePlayer.getUUID(),
                    fakePlayer.getGameProfile().getName(),
                    setting.skin(),
                    setting.model(),
                    pngBytes,
                    false
            );
        } catch (IOException exception) {
            Curtain.LOGGER.warn("Unable to sync fake player skin {} for {}", setting.skin(), fakePlayer.getGameProfile().getName(), exception);
            return null;
        }
    }

    public static synchronized Path getSkinDirectory(MinecraftServer server) {
        return getCurtainDirectory(server).resolve("skins");
    }

    public static synchronized void stopSkinServer() {
        loadedServer = null;
        SKINS.clear();
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (loadedServer == server) return;
        loadedServer = server;
        SKINS.clear();
        Path config = getConfigPath(server);
        if (!Files.isRegularFile(config)) return;
        try (BufferedReader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject object = entry.getValue().getAsJsonObject();
                String skin = object.get("skin").getAsString();
                String model = object.has("model") ? object.get("model").getAsString() : "classic";
                SKINS.put(entry.getKey().toLowerCase(Locale.ROOT), new SkinSetting(skin, normalizeModel(model)));
            }
        } catch (Exception exception) {
            Curtain.LOGGER.warn("Unable to load fake player skin config", exception);
        }
    }

    private static void save(MinecraftServer server) throws IOException {
        Path config = getConfigPath(server);
        Files.createDirectories(config.getParent());
        JsonObject root = new JsonObject();
        for (Map.Entry<String, SkinSetting> entry : SKINS.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("skin", entry.getValue().skin());
            object.addProperty("model", entry.getValue().model());
            root.add(entry.getKey(), object);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(config, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static Path getConfigPath(MinecraftServer server) {
        return getCurtainDirectory(server).resolve(CONFIG_FILE);
    }

    private static Path getCurtainDirectory(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve("curtain").normalize();
    }

    private static Path getSkinPath(MinecraftServer server, String skin) {
        return getSkinDirectory(server).resolve(skin).normalize();
    }

    private static void validateSkinName(String skin) {
        Path skinPath = Path.of(skin);
        if (skinPath.isAbsolute() || skin.contains("..") || skin.contains("/") || skin.contains("\\") || !skin.matches("[A-Za-z0-9_.-]+\\.png")) {
            throw new IllegalArgumentException("Skin name must be a png file name, for example steve.png");
        }
    }

    private static String normalizeModel(String model) {
        if ("slim".equalsIgnoreCase(model)) {
            return "slim";
        }
        if ("classic".equalsIgnoreCase(model)) {
            return "classic";
        }
        throw new IllegalArgumentException("Skin model must be classic or slim");
    }

    private static byte[] readSkinBytes(MinecraftServer server, String skin) throws IOException {
        validateSkinName(skin);
        Path skinPath = getSkinPath(server, skin);
        Path skinDir = getSkinDirectory(server).toAbsolutePath().normalize();
        Path absoluteSkinPath = skinPath.toAbsolutePath().normalize();
        if (!absoluteSkinPath.startsWith(skinDir) || !Files.isRegularFile(absoluteSkinPath)) {
            throw new IOException("Skin file does not exist: " + skinPath);
        }
        long size = Files.size(absoluteSkinPath);
        if (size > MAX_PNG_BYTES) {
            throw new IOException("Skin file is too large: " + size + " bytes (max " + MAX_PNG_BYTES + ")");
        }
        return Files.readAllBytes(absoluteSkinPath);
    }

    private static void sendToPlayer(ServerPlayer player, FakePlayerSkinSyncPacket packet) {
        if (CurtainNetwork.CHANNEL.isRemotePresent(player.connection.connection) || player.connection.connection.isMemoryConnection()) {
            CurtainNetwork.sendToPlayer(player, packet);
        }
    }
}
