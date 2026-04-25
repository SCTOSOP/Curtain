package dev.dubhe.curtain.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class FakePlayerSkinSyncPacket {
    public static final int MAX_PNG_BYTES = 512 * 1024;

    private final UUID playerUuid;
    private final String playerName;
    private final String skinFileName;
    private final String model;
    private final byte[] pngBytes;
    private final boolean clear;

    public FakePlayerSkinSyncPacket(UUID playerUuid, String playerName, String skinFileName, String model, byte[] pngBytes, boolean clear) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.skinFileName = skinFileName;
        this.model = model;
        this.pngBytes = pngBytes;
        this.clear = clear;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String skinFileName() {
        return skinFileName;
    }

    public String model() {
        return model;
    }

    public byte[] pngBytes() {
        return pngBytes;
    }

    public boolean clear() {
        return clear;
    }

    public static void encode(FakePlayerSkinSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerUuid);
        buf.writeUtf(packet.playerName);
        buf.writeUtf(packet.skinFileName);
        buf.writeUtf(packet.model);
        buf.writeBoolean(packet.clear);
        buf.writeByteArray(packet.pngBytes);
    }

    public static FakePlayerSkinSyncPacket decode(FriendlyByteBuf buf) {
        UUID playerUuid = buf.readUUID();
        String playerName = buf.readUtf();
        String skinFileName = buf.readUtf();
        String model = buf.readUtf();
        boolean clear = buf.readBoolean();
        byte[] pngBytes = buf.readByteArray(MAX_PNG_BYTES);
        return new FakePlayerSkinSyncPacket(playerUuid, playerName, skinFileName, model, pngBytes, clear);
    }

    public static void handle(FakePlayerSkinSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientFakePlayerSkinPacketHandler.handle(packet));
        context.setPacketHandled(true);
    }
}
