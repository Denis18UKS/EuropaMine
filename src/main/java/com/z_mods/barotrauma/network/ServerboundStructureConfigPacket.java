package com.z_mods.barotrauma.network;

import com.z_mods.barotrauma.structure.StructureSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ServerboundStructureConfigPacket(InteractionHand hand, StructureSettings.Config config) {
    public static void encode(ServerboundStructureConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        buffer.writeUtf(packet.config.kind().id());
        buffer.writeUtf(packet.config.wallType().id());
        buffer.writeBoolean(packet.config.unbreakable());
        buffer.writeBoolean(packet.config.noAiTarget());
        buffer.writeFloat(packet.config.health());
    }

    public static ServerboundStructureConfigPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        StructureSettings.Kind kind = StructureSettings.Kind.byId(buffer.readUtf());
        StructureSettings.WallType wallType = StructureSettings.WallType.byId(buffer.readUtf());
        boolean unbreakable = buffer.readBoolean();
        boolean noAiTarget = buffer.readBoolean();
        float health = buffer.readFloat();
        return new ServerboundStructureConfigPacket(hand,
                new StructureSettings.Config(kind, wallType, unbreakable, noAiTarget, health));
    }

    public static void handle(ServerboundStructureConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.getAbilities().instabuild) {
                return;
            }

            ItemStack stack = player.getItemInHand(packet.hand);
            if (!(stack.getItem() instanceof BlockItem)) {
                return;
            }

            StructureSettings.write(stack, packet.config);
            stack.setHoverName(getDisplayName(packet.config));
            player.getInventory().setChanged();
        });
        context.setPacketHandled(true);
    }

    private static Component getDisplayName(StructureSettings.Config config) {
        if (config.kind() == StructureSettings.Kind.PLATFORM) {
            return Component.literal("\u0411\u0430\u0437\u043e\u0432\u0430\u044f \u043f\u043b\u0430\u0442\u0444\u043e\u0440\u043c\u0430");
        }
        return config.wallType() == StructureSettings.WallType.INTERNAL
                ? Component.literal("\u0412\u043d\u0443\u0442\u0440\u0435\u043d\u043d\u044f\u044f \u0441\u0442\u0435\u043d\u0430")
                : Component.literal("\u0412\u043d\u0435\u0448\u043d\u044f\u044f \u0441\u0442\u0435\u043d\u0430");
    }
}
