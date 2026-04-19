package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.blocks.NavigationTerminal;
import com.z_mods.barotrauma.blocks.VentDeco;
import com.z_mods.barotrauma.blocks.VentDecoInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Barotrauma.MOD_ID);

    public static final RegistryObject<Block> VENT_DECO = registerBlock("vent_deco",
            VentDeco::new);
    
    public static final RegistryObject<Block> VENT_DECO_INT = registerBlock("vent_deco_int",
            VentDecoInt::new);

    public static final RegistryObject<Block> NAVIGATION_TERMINAL = registerBlock("navigation_terminal",
            NavigationTerminal::new);

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties()));
    }
}
