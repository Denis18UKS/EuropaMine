package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockGroup {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Barotrauma.MOD_ID);
    public static final RegistryObject<CreativeModeTab> BAROTRAUMA_TAB = CREATIVE_MODE_TABS.register("barotrauma_tab", () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.VENT_DECO.get())).title(Component.translatable("creativetab.barotrauma_tab")).displayItems((p, out) -> {
        out.accept(ModBlocks.VENT_DECO.get()); out.accept(ModBlocks.VENT_DECO_INT.get()); out.accept(ModBlocks.NAVIGATION_TERMINAL.get()); out.accept(ModBlocks.SETTINGS_PANEL.get()); out.accept(ModBlocks.HOTBAR_LAYOUT_PANEL.get()); out.accept(ModBlocks.MEDIUM_STEEL_CABINET.get()); out.accept(ModBlocks.LARGE_STEEL_CABINET.get()); out.accept(ModBlocks.BEDS.get()); out.accept(ModBlocks.JUNCTION_BOX.get()); out.accept(ModBlocks.SUBMARINE_BUTTON_BLOCK.get()); out.accept(ModBlocks.SUBMARINE_DOOR.get()); out.accept(ModBlocks.SUBMARINE_LEVER.get()); out.accept(ModBlocks.SUBMARINE_TRAPDOOR.get());
    }).build());
    public static final RegistryObject<CreativeModeTab> BAROTRAUMA_TOOLS_TAB = CREATIVE_MODE_TABS.register("barotrauma_tools_tab", () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.WRENCH.get())).title(Component.translatable("creativetab.barotrauma_tools_tab")).displayItems((p, out) -> {
        out.accept(ModItems.NAMETAG.get()); out.accept(ModItems.ACCESS_CONFIGURATOR.get()); out.accept(ModItems.SLOT_LOCK_TOOL.get()); out.accept(ModItems.GARNITURE.get()); out.accept(ModItems.OXYGEN_MASK.get()); out.accept(ModItems.WRENCH.get()); out.accept(ModItems.SCREWDIN.get()); out.accept(ModItems.CROWBAR.get()); out.accept(ModItems.WELDING_MACHINE.get()); out.accept(ModItems.CUTTER.get()); out.accept(ModItems.OXYGEN_TANK.get()); out.accept(ModItems.WELDING_MACHINE_FUEL_TANK.get()); out.accept(ModItems.TOOL_BELT.get()); out.accept(ModItems.CRATE.get()); out.accept(ModItems.LARGE_CRATE.get()); out.accept(ModItems.PANEL_CAMERA.get()); out.accept(ModItems.GUI_BINDER.get()); out.accept(ModItems.NAVIGATION_LINKER.get()); out.accept(ModItems.ACTIVE_HAND_SONAR.get()); out.accept(ModItems.SLOT_BINDING_CONFIGURATOR.get()); out.accept(ModItems.RED_WIRE_TOOL.get()); out.accept(ModItems.BLUE_WIRE_TOOL.get()); out.accept(ModItems.REACTOR_FUEL_ROD.get());
    }).build());
    public static final RegistryObject<CreativeModeTab> BAROTRAUMA_BUILD_TAB = CREATIVE_MODE_TABS.register("barotrauma_build_tab", () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.SUBMARINE_BUILDER.get())).title(Component.translatable("creativetab.barotrauma_build_tab")).displayItems((p, out) -> {
        out.accept(ModItems.SUBMARINE_BUILDER.get()); out.accept(ModItems.SUBMARINE_DRAWING_TOOL.get()); out.accept(ModItems.EXTERNAL_WALL_ZONE_TOOL.get()); out.accept(ModItems.INTERNAL_WALL_ZONE_TOOL.get()); out.accept(ModItems.FLOOR_ZONE_TOOL.get());
    }).build());
}
