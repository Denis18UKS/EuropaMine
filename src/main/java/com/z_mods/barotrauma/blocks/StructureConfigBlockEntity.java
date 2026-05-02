package com.z_mods.barotrauma.blocks;

import com.z_mods.barotrauma.init.ModBlockEntities;
import com.z_mods.barotrauma.init.ModBlocks;
import com.z_mods.barotrauma.structure.StructureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class StructureConfigBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String CONFIGURED_TAG = "Configured";
    private static final String DOOR_OPEN_TAG = "DoorOpen";
    private static final String BUTTON_PRESSED_UNTIL_TAG = "ButtonPressedUntil";
    private static final RawAnimation DOOR_OPEN = RawAnimation.begin().thenPlayAndHold("door_open");
    private static final RawAnimation DOOR_CLOSE = RawAnimation.begin().thenPlayAndHold("door_close");
    private static final RawAnimation BUTTON_CLICK = RawAnimation.begin().thenPlay("cb");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private StructureSettings.Config config = new StructureSettings.Config(
            StructureSettings.Kind.PLATFORM,
            StructureSettings.WallType.INTERNAL,
            false,
            false,
            100.0F
    );
    private boolean configured;
    private boolean doorOpen;
    private long buttonPressedUntil;

    public StructureConfigBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.STRUCTURE_CONFIG.get(), pos, state);
    }

    protected StructureConfigBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public StructureSettings.Config getConfig() {
        return config;
    }

    public boolean hasConfig() {
        return configured;
    }

    public void setConfig(StructureSettings.Config config) {
        this.config = config;
        this.configured = true;
        setChanged();
        sync();
    }

    public boolean isDoorOpen() {
        return doorOpen;
    }

    public void setDoorOpen(boolean doorOpen) {
        this.doorOpen = doorOpen;
        setChanged();
        sync();
        triggerAnim("structure_controller", doorOpen ? "door_open" : "door_close");
    }

    public boolean isButtonPressed() {
        return this.level != null && this.level.getGameTime() < buttonPressedUntil;
    }

    public void triggerButtonClick() {
        this.buttonPressedUntil = this.level == null ? 0L : this.level.getGameTime() + 12L;
        setChanged();
        sync();
        triggerAnim("structure_controller", "button_click");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(CONFIGURED_TAG, this.configured);
        tag.putBoolean(DOOR_OPEN_TAG, this.doorOpen);
        tag.putLong(BUTTON_PRESSED_UNTIL_TAG, this.buttonPressedUntil);
        if (this.configured) {
            StructureSettings.write(tag, this.config);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.configured = tag.getBoolean(CONFIGURED_TAG) || tag.contains(StructureSettings.TAG);
        this.doorOpen = tag.getBoolean(DOOR_OPEN_TAG);
        this.buttonPressedUntil = tag.getLong(BUTTON_PRESSED_UNTIL_TAG);
        if (this.configured) {
            this.config = StructureSettings.read(tag);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "structure_controller", 0, state -> {
            if (getBlockState().is(ModBlocks.SUBMARINE_BUTTON_BLOCK.get())) {
                return isButtonPressed() ? state.setAndContinue(BUTTON_CLICK) : PlayState.STOP;
            }
            return this.doorOpen ? state.setAndContinue(DOOR_OPEN) : PlayState.STOP;
        }).triggerableAnim("door_open", DOOR_OPEN)
                .triggerableAnim("door_close", DOOR_CLOSE)
                .triggerableAnim("button_click", BUTTON_CLICK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    private void sync() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
