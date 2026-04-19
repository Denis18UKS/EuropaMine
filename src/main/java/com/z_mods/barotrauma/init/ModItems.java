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
}
