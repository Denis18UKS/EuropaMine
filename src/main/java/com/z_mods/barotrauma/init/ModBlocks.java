package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.blocks.NavigationTerminal;
import com.z_mods.barotrauma.blocks.SimpleHorizontalBlock;
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

    public static final RegistryObject<Block> BEDS = registerBlock("beds",
            SimpleHorizontalBlock::new);

    public static final RegistryObject<Block> JUNCTION_BOX = registerBlock("junction_box",
            SimpleHorizontalBlock::new);

    public static final RegistryObject<Block> SUBMARINE_BUTTON_BLOCK = registerBlock("submarine_button_block",
            SimpleHorizontalBlock::new);

    public static final RegistryObject<Block> SUBMARINE_DOOR = registerBlock("submarine_door",
            SimpleHorizontalBlock::new);

    public static final RegistryObject<Block> SUBMARINE_LEVER = registerBlock("submarine_lever",
            SimpleHorizontalBlock::new);

    public static final RegistryObject<Block> SUBMARINE_TRAPDOOR = registerBlock("submarine_trapdoor",
            SimpleHorizontalBlock::new);

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
