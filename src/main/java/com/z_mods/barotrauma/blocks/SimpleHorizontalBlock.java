package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.init.ModBlocks;
import com.z_mods.barotrauma.structure.StructureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SimpleHorizontalBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final VoxelShape shapeNorth;
    private final VoxelShape shapeSouth;
    private final VoxelShape shapeWest;
    private final VoxelShape shapeEast;

    public SimpleHorizontalBlock() {
        this(Shapes.block());
    }

    public SimpleHorizontalBlock(VoxelShape shapeNorth) {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .pushReaction(PushReaction.NORMAL)
                .noOcclusion());

        this.shapeNorth = shapeNorth;
        this.shapeSouth = rotate(shapeNorth, Direction.SOUTH);
        this.shapeWest = rotate(shapeNorth, Direction.WEST);
        this.shapeEast = rotate(shapeNorth, Direction.EAST);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (this == ModBlocks.SUBMARINE_DOOR.get() || this == ModBlocks.SUBMARINE_BUTTON_BLOCK.get()) {
            return RenderShape.ENTITYBLOCK_ANIMATED;
        }
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (this == ModBlocks.SUBMARINE_DOOR.get()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof StructureConfigBlockEntity blockEntity) {
                boolean open = !blockEntity.isDoorOpen();
                blockEntity.setDoorOpen(open);
                level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (this == ModBlocks.SUBMARINE_BUTTON_BLOCK.get()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof StructureConfigBlockEntity blockEntity) {
                blockEntity.triggerButtonClick();
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StructureConfigBlockEntity blockEntity
                && stack.hasTag() && stack.getTag().contains(StructureSettings.TAG)) {
            blockEntity.setConfig(StructureSettings.read(stack));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructureConfigBlockEntity(pos, state);
    }

    private VoxelShape shapeFor(Direction direction) {
        return switch (direction) {
            case SOUTH -> this.shapeSouth;
            case WEST -> this.shapeWest;
            case EAST -> this.shapeEast;
            default -> this.shapeNorth;
        };
    }

    private static VoxelShape rotate(VoxelShape shape, Direction direction) {
        VoxelShape result = Shapes.empty();
        for (var box : shape.toAabbs()) {
            result = Shapes.or(result, switch (direction) {
                case SOUTH -> Shapes.box(1.0D - box.maxX, box.minY, 1.0D - box.maxZ,
                        1.0D - box.minX, box.maxY, 1.0D - box.minZ);
                case WEST -> Shapes.box(box.minZ, box.minY, 1.0D - box.maxX,
                        box.maxZ, box.maxY, 1.0D - box.minX);
                case EAST -> Shapes.box(1.0D - box.maxZ, box.minY, box.minX,
                        1.0D - box.minZ, box.maxY, box.maxX);
                default -> Shapes.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            });
        }
        return result.optimize();
    }
}
