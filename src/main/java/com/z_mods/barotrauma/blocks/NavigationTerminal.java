package com.z_mods.barotrauma.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class NavigationTerminal extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE_NORTH = Shapes.box(-1.0, 0.125, 0.3125, 2.0, 2.0, 1.0);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(-1.0, 0.125, 0.0, 2.0, 2.0, 0.6875);
    private static final VoxelShape SHAPE_WEST = Shapes.box(0.3125, 0.125, -1.0, 1.0, 2.0, 2.0);
    private static final VoxelShape SHAPE_EAST = Shapes.box(0.0, 0.125, -1.0, 0.6875, 2.0, 2.0);

    public NavigationTerminal() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.NORMAL)
                .noOcclusion()
        );

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);

        return switch (facing) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
