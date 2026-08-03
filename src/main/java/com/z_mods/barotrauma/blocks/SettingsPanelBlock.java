package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.network.PanelNetworkSync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A single placed controller automatically expands to a 7x4 wall panel. */
public final class SettingsPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final int WIDTH = 7;
    public static final int HEIGHT = 4;
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, WIDTH - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, HEIGHT - 1);
    private static final ThreadLocal<Boolean> ASSEMBLING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape NORTH = Shapes.box(0, 0, 0.875, 1, 1, 1);
    private static final VoxelShape SOUTH = Shapes.box(0, 0, 0, 1, 1, 0.125);
    private static final VoxelShape WEST = Shapes.box(0.875, 0, 0, 1, 1, 1);
    private static final VoxelShape EAST = Shapes.box(0, 0, 0, 0.125, 1, 1);

    public SettingsPanelBlock() {
        super(Properties.of().mapColor(MapColor.METAL).strength(4.0F, 8.0F)
                .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(COLUMN, 0).setValue(ROW, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || ASSEMBLING.get()) return;
        Direction facing = state.getValue(FACING);
        Direction right = facing.getCounterClockWise();
        for (int row = 0; row < HEIGHT; row++) {
            for (int column = 0; column < WIDTH; column++) {
                BlockPos part = pos.relative(right, column).above(row);
                if (!part.equals(pos) && !level.getBlockState(part).canBeReplaced()) {
                    level.destroyBlock(pos, true);
                    if (placer instanceof Player player) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.barotrauma.panel_obstructed"), true);
                    }
                    return;
                }
            }
        }
        ASSEMBLING.set(true);
        try {
            for (int row = 0; row < HEIGHT; row++) {
                for (int column = 0; column < WIDTH; column++) {
                    BlockPos part = pos.relative(right, column).above(row);
                    BlockState partState = defaultBlockState().setValue(FACING, facing)
                            .setValue(COLUMN, column).setValue(ROW, row);
                    level.setBlock(part, partState, Block.UPDATE_ALL);
                }
            }
        } finally {
            ASSEMBLING.set(false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && !ASSEMBLING.get() && !DISMANTLING.get()) {
            BlockPos origin = origin(pos, state);
            Direction right = state.getValue(FACING).getCounterClockWise();
            DISMANTLING.set(true);
            try {
                for (int row = 0; row < HEIGHT; row++) {
                    for (int column = 0; column < WIDTH; column++) {
                        BlockPos part = origin.relative(right, column).above(row);
                        if (!part.equals(pos) && level.getBlockState(part).is(this)) {
                            level.removeBlock(part, false);
                        }
                    }
                }
            } finally {
                DISMANTLING.set(false);
            }
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    public static BlockPos origin(BlockPos pos, BlockState state) {
        Direction right = state.getValue(FACING).getCounterClockWise();
        return pos.relative(right, -state.getValue(COLUMN)).below(state.getValue(ROW));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PanelNetworkSync.openSettings(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0
                ? new SettingsPanelBlockEntity(pos, state) : null;
    }

    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
