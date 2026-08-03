package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.network.PowerPackets;
import com.z_mods.barotrauma.power.PowerSystem;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Map-building tool used to search, select and bind any registered GUI to a block position. */
public final class GuiBinderItem extends Item {
    public static final String SELECTED_GUI = "SelectedGui";

    public GuiBinderItem(Properties properties) {
        super(properties);
    }

    public static String selected(ItemStack stack) {
        String value = stack.getOrCreateTag().getString(SELECTED_GUI);
        if (!PowerSystem.isKnownGui(value)) value = PowerWorldData.REACTOR_GUI;
        return value;
    }

    public static void select(ItemStack stack, String guiId) {
        if (PowerSystem.isKnownGui(guiId)) stack.getOrCreateTag().putString(SELECTED_GUI, guiId);
    }

    private static boolean canConfigure(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!canConfigure(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.literal(
                        "Инструмент привязки GUI доступен только оператору или в творческом режиме."), true);
                return InteractionResultHolder.fail(stack);
            }
            PowerPackets.sendOpenBinder(serverPlayer, selected(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!canConfigure(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.literal(
                    "Инструмент привязки GUI доступен только оператору или в творческом режиме."), true);
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos();
        PowerWorldData data = PowerWorldData.get(serverLevel);
        if (serverPlayer.isShiftKeyDown()) {
            boolean removed = data.unbind(pos);
            int wires = data.disconnect(pos);
            serverPlayer.displayClientMessage(Component.literal(removed
                    ? "GUI отвязан. Удалено проводов: " + wires
                    : "На этом блоке GUI не был привязан."), true);
            return InteractionResult.SUCCESS;
        }

        String guiId = selected(context.getItemInHand());
        data.bind(pos, guiId);
        String title = PowerSystem.GUI_CATALOG.stream()
                .filter(entry -> entry.id().equals(guiId))
                .map(PowerSystem.GuiEntry::title)
                .findFirst().orElse(guiId);
        serverPlayer.displayClientMessage(Component.literal("Привязан GUI «" + title + "» к " + pos.toShortString()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        String guiId = selected(stack);
        String title = PowerSystem.GUI_CATALOG.stream()
                .filter(entry -> entry.id().equals(guiId))
                .map(PowerSystem.GuiEntry::title)
                .findFirst().orElse(guiId);
        lines.add(Component.literal("Выбрано: " + title).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("ПКМ в воздух: список GUI").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("ПКМ по блоку: привязать").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Shift + ПКМ: удалить привязку").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal("Требуется оператор или творческий режим").withStyle(ChatFormatting.DARK_RED));
    }
}
