package dev.dubhe.curtain.network;

import dev.dubhe.curtain.features.player.client.CurtainClientSkinManager;

public class ClientFakePlayerSkinPacketHandler {
    public static void handle(FakePlayerSkinSyncPacket packet) {
        if (packet.clear()) {
            CurtainClientSkinManager.clear(packet.playerUuid());
            return;
        }
        CurtainClientSkinManager.registerFromBytes(packet.playerUuid(), packet.skinFileName(), packet.model(), packet.pngBytes());
    }
}
