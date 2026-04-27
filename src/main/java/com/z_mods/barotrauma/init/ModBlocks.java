package com.z_mods.barotrauma.init;

import com.z_mods.barotrauma.Barotrauma;
import com.z_mods.barotrauma.blocks.NavigationTerminal;
import com.z_mods.barotrauma.blocks.SimpleHorizontalBlock;
import com.z_mods.barotrauma.blocks.VentDeco;
import com.z_mods.barotrauma.blocks.VentDecoInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
            () -> new SimpleHorizontalBlock(box(-4, 0, 0, 32, 32, 16)));

    public static final RegistryObject<Block> JUNCTION_BOX = registerBlock("junction_box",
            () -> new SimpleHorizontalBlock(box(1, 0, 14, 15, 20, 16)));

    public static final RegistryObject<Block> SUBMARINE_BUTTON_BLOCK = registerBlock("submarine_button_block",
            () -> new SimpleHorizontalBlock(box(5, 4, 13.5, 11, 14, 16)));

    public static final RegistryObject<Block> SUBMARINE_DOOR = registerBlock("submarine_door",
            () -> new SimpleHorizontalBlock(box(0, 0, 7, 16, 32, 9)));

    public static final RegistryObject<Block> SUBMARINE_LEVER = registerBlock("submarine_lever",
            () -> new SimpleHorizontalBlock(box(-1.25, 5, 13.25, 17.75, 24, 16.75)));

    public static final RegistryObject<Block> SUBMARINE_TRAPDOOR = registerBlock("submarine_trapdoor",
            () -> new SimpleHorizontalBlock(box(0, 0, 0, 16, 2, 16)));

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties()));
    }

    private static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return Shapes.box(minX / 16.0D, minY / 16.0D, minZ / 16.0D,
                maxX / 16.0D, maxY / 16.0D, maxZ / 16.0D);
    }
}
