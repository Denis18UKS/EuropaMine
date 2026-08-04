package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.item.AccessConfiguratorItem;
import com.z_mods.barotrauma.item.AccessNameTagItem;
import com.z_mods.barotrauma.item.GarnitureHelmetItem;
import com.z_mods.barotrauma.item.GuiBinderItem;
import com.z_mods.barotrauma.item.NavigationLinkerItem;
import com.z_mods.barotrauma.item.PanelCameraItem;
import com.z_mods.barotrauma.item.SubmarineBuilderItem;
import com.z_mods.barotrauma.item.WireToolItem;
import com.z_mods.barotrauma.power.PowerWorldData;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Barotrauma.MOD_ID);

    public static final RegistryObject<Item> SLOT_LOCK_TOOL = ITEMS.register("slot_lock_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PANEL_CAMERA = ITEMS.register("panel_camera",
            () -> new PanelCameraItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GUI_BINDER = ITEMS.register("gui_binder",
            () -> new GuiBinderItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NAVIGATION_LINKER = ITEMS.register("navigation_linker",
            () -> new NavigationLinkerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SUBMARINE_BUILDER = ITEMS.register("submarine_builder",
            () -> new SubmarineBuilderItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ACTIVE_HAND_SONAR = ITEMS.register("active_hand_sonar",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RED_WIRE_TOOL = ITEMS.register("red_wire_tool",
            () -> new WireToolItem(PowerWorldData.WireColor.RED, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BLUE_WIRE_TOOL = ITEMS.register("blue_wire_tool",
            () -> new WireToolItem(PowerWorldData.WireColor.BLUE, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> REACTOR_FUEL_ROD = ITEMS.register("reactor_fuel_rod",
            () -> new Item(new Item.Properties().stacksTo(1).durability(24_000)));

    public static final RegistryObject<Item> ACCESS_CONFIGURATOR = ITEMS.register("access_configurator",
            () -> new AccessConfiguratorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NAMETAG = ITEMS.register("nametag",
            () -> new AccessNameTagItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> GARNITURE = ITEMS.register("garniture",
            () -> new GarnitureHelmetItem(ArmorMaterials.LEATHER, new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> WRENCH = ITEMS.register("wrench",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SCREWDIN = ITEMS.register("screwdin",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> OXYGEN_TANK = ITEMS.register("oxygen_tank",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> WELDING_MACHINE_FUEL_TANK = ITEMS.register("welding_machine_fuel_tank",
            () -> new Item(new Item.Properties().stacksTo(1)));
}
