package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.blocks.AnimatedStructureConfigBlockEntity;
import com.z_mods.barotrauma.blocks.VentDecoEntity;
import com.z_mods.barotrauma.blocks.VentDecoIntEntity;
import com.z_mods.barotrauma.blocks.StructureConfigBlockEntity;
import com.z_mods.barotrauma.blocks.SettingsPanelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Barotrauma.MOD_ID);
    
    public static final RegistryObject<BlockEntityType<VentDecoEntity>> VENT_DECO = 
        BLOCK_ENTITIES.register("vent_deco",
            () -> BlockEntityType.Builder.of(VentDecoEntity::new, ModBlocks.VENT_DECO.get()).build(null));

    public static final RegistryObject<BlockEntityType<VentDecoIntEntity>> VENT_DECO_INT = 
        BLOCK_ENTITIES.register("vent_deco_int",
            () -> BlockEntityType.Builder.of(VentDecoIntEntity::new, ModBlocks.VENT_DECO_INT.get()).build(null));

    public static final RegistryObject<BlockEntityType<StructureConfigBlockEntity>> STRUCTURE_CONFIG =
        BLOCK_ENTITIES.register("structure_config",
            () -> BlockEntityType.Builder.of(StructureConfigBlockEntity::new,
                    ModBlocks.BEDS.get(),
                    ModBlocks.JUNCTION_BOX.get(),
                    ModBlocks.SUBMARINE_LEVER.get(),
                    ModBlocks.SUBMARINE_TRAPDOOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<AnimatedStructureConfigBlockEntity>> ANIMATED_STRUCTURE_CONFIG =
        BLOCK_ENTITIES.register("animated_structure_config",
            () -> BlockEntityType.Builder.of(AnimatedStructureConfigBlockEntity::new,
                    ModBlocks.SUBMARINE_BUTTON_BLOCK.get(),
                    ModBlocks.SUBMARINE_DOOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<SettingsPanelBlockEntity>> SETTINGS_PANEL =
        BLOCK_ENTITIES.register("settings_panel",
            () -> BlockEntityType.Builder.of(SettingsPanelBlockEntity::new,
                    ModBlocks.SETTINGS_PANEL.get()).build(null));
}
