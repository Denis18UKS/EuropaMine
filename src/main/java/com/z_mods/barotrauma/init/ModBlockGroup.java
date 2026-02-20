package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockGroup {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Barotrauma.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BAROTRAUMA_TAB = CREATIVE_MODE_TABS.register("barotrauma_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocks.VENT_DECO.get()))
                    .title(Component.translatable("creativetab.barotrauma_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.VENT_DECO.get());
                        pOutput.accept(ModBlocks.VENT_DECO_INT.get());
                    })
                    .build());
}