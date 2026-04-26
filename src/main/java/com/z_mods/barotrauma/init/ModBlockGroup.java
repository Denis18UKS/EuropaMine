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
                        pOutput.accept(ModBlocks.NAVIGATION_TERMINAL.get());
                        pOutput.accept(ModBlocks.BEDS.get());
                        pOutput.accept(ModBlocks.JUNCTION_BOX.get());
                        pOutput.accept(ModBlocks.SUBMARINE_BUTTON_BLOCK.get());
                        pOutput.accept(ModBlocks.SUBMARINE_DOOR.get());
                        pOutput.accept(ModBlocks.SUBMARINE_LEVER.get());
                        pOutput.accept(ModBlocks.SUBMARINE_TRAPDOOR.get());
                    })
                    .build());

    public static final RegistryObject<CreativeModeTab> BAROTRAUMA_TOOLS_TAB = CREATIVE_MODE_TABS.register("barotrauma_tools_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.ACCESS_CONFIGURATOR.get()))
                    .title(Component.translatable("creativetab.barotrauma_tools_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModItems.NAMETAG.get());
                        pOutput.accept(ModItems.ACCESS_CONFIGURATOR.get());
                        pOutput.accept(ModItems.SLOT_LOCK_TOOL.get());
                        pOutput.accept(ModItems.GARNITURE.get());
                        pOutput.accept(ModItems.WRENCH.get());
                        pOutput.accept(ModItems.SCREWDIN.get());
                        pOutput.accept(ModItems.CROWBAR.get());
                        pOutput.accept(ModItems.OXYGEN_TANK.get());
                        pOutput.accept(ModItems.WELDING_MACHINE_FUEL_TANK.get());
                    })
                    .build());
}
