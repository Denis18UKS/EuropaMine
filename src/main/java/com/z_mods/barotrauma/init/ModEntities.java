package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.entity.SubmarineContraptionEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Barotrauma.MOD_ID);

    public static final RegistryObject<EntityType<SubmarineContraptionEntity>> SUBMARINE_CONTRAPTION =
            ENTITY_TYPES.register("submarine_contraption", () -> EntityType.Builder
                    .<SubmarineContraptionEntity>of(SubmarineContraptionEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(256)
                    .updateInterval(1)
                    .build("submarine_contraption"));

    private ModEntities() {}
}
