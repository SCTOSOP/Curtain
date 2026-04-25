package dev.dubhe.curtain.network;

import dev.dubhe.curtain.Curtain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class CurtainNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Curtain.MODID, "main"),
            () -> PROTOCOL_VERSION,
            version -> PROTOCOL_VERSION.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version),
            version -> PROTOCOL_VERSION.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version)
    );

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                FakePlayerSkinSyncPacket.class,
                FakePlayerSkinSyncPacket::encode,
                FakePlayerSkinSyncPacket::decode,
                FakePlayerSkinSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void sendToAll(FakePlayerSkinSyncPacket packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, FakePlayerSkinSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
