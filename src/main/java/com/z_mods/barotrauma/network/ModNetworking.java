package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Barotrauma.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId;

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.messageBuilder(ServerboundStructureConfigPacket.class, packetId++)
                .encoder(ServerboundStructureConfigPacket::encode)
                .decoder(ServerboundStructureConfigPacket::decode)
                .consumerMainThread(ServerboundStructureConfigPacket::handle)
                .add();

        CHANNEL.messageBuilder(PanelPackets.ClientboundOpenPanel.class, packetId++)
                .encoder(PanelPackets.ClientboundOpenPanel::encode)
                .decoder(PanelPackets.ClientboundOpenPanel::decode)
                .consumerMainThread(PanelPackets.ClientboundOpenPanel::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ClientboundOpenCamera.class, packetId++)
                .encoder(PanelPackets.ClientboundOpenCamera::encode)
                .decoder(PanelPackets.ClientboundOpenCamera::decode)
                .consumerMainThread(PanelPackets.ClientboundOpenCamera::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ClientboundSettings.class, packetId++)
                .encoder(PanelPackets.ClientboundSettings::encode)
                .decoder(PanelPackets.ClientboundSettings::decode)
                .consumerMainThread(PanelPackets.ClientboundSettings::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ClientboundPhoto.class, packetId++)
                .encoder(PanelPackets.ClientboundPhoto::encode)
                .decoder(PanelPackets.ClientboundPhoto::decode)
                .consumerMainThread(PanelPackets.ClientboundPhoto::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ServerboundSettings.class, packetId++)
                .encoder(PanelPackets.ServerboundSettings::encode)
                .decoder(PanelPackets.ServerboundSettings::decode)
                .consumerMainThread(PanelPackets.ServerboundSettings::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ServerboundPhotoUpload.class, packetId++)
                .encoder(PanelPackets.ServerboundPhotoUpload::encode)
                .decoder(PanelPackets.ServerboundPhotoUpload::decode)
                .consumerMainThread(PanelPackets.ServerboundPhotoUpload::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ServerboundPhotoDelete.class, packetId++)
                .encoder(PanelPackets.ServerboundPhotoDelete::encode)
                .decoder(PanelPackets.ServerboundPhotoDelete::decode)
                .consumerMainThread(PanelPackets.ServerboundPhotoDelete::handle).add();
        CHANNEL.messageBuilder(PanelPackets.ServerboundStartSession.class, packetId++)
                .encoder(PanelPackets.ServerboundStartSession::encode)
                .decoder(PanelPackets.ServerboundStartSession::decode)
                .consumerMainThread(PanelPackets.ServerboundStartSession::handle).add();
    }
}
