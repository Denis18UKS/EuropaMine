package com.z_mods.barotrauma.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class CabinetBlock extends BaseEntityBlock {
    public enum Size {
        MEDIUM(20, 3, "Средний стальной шкаф"),
        LARGE(35, 5, "Большой стальной шкаф");

        private final int mainSlots;
        private final int specialSlots;
        private final String title;

        Size(int mainSlots, int specialSlots, String title) {
            this.mainSlots = mainSlots;
            this.specialSlots = specialSlots;
            this.title = title;
        }

        public int mainSlots() { return mainSlots; }
        public int specialSlots() { return specialSlots; }
        public int totalSlots() { return mainSlots + specialSlots; }
        public String title() { return title; }
    }

    private final Size size;

    public CabinetBlock(Size size, Properties properties) {
        super(properties);
        this.size = size;
    }

    public Size size() { return size; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CabinetBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
            NetworkHooks.openScreen(serverPlayer, cabinet, buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeBoolean(size == Size.LARGE);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CabinetBlockEntity cabinet) {
            net.minecraft.world.Containers.dropContents(level, pos, cabinet);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
