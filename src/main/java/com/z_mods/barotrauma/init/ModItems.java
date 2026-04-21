package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Barotrauma.MOD_ID);

    public static final RegistryObject<Item> SLOT_LOCK_TOOL = ITEMS.register("slot_lock_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));

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
