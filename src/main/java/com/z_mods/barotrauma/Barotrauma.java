package com.z_mods.barotrauma;

import com.mojang.logging.LogUtils;
import com.z_mods.barotrauma.init.ModBlocks;
import com.z_mods.barotrauma.init.ModBlockGroup;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.init.ModBlockEntities;
import com.z_mods.barotrauma.init.ModMenus; // ВАШ класс
import com.z_mods.barotrauma.blocks.StructureConfigBlockRenderer;
import com.z_mods.barotrauma.blocks.VentDecoRenderer;
import com.z_mods.barotrauma.blocks.VentDecoIntRenderer;
import com.z_mods.barotrauma.client.SettingsPanelRenderer;
import com.z_mods.barotrauma.client.VentScreen; // ВАШ класс
import com.z_mods.barotrauma.network.ModNetworking;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens; // ЭТО ПРАВИЛЬНО - КЛАСС MINECRAFT
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod(Barotrauma.MOD_ID)
public class Barotrauma {
    public static final String MOD_ID = "barotrauma";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Barotrauma() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GeckoLib.initialize();

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockGroup.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus); // Регистрируем меню

        ModNetworking.register();
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModBlockGroup.BAROTRAUMA_TAB.get()) {
            event.accept(ModBlocks.VENT_DECO.get());
            event.accept(ModBlocks.VENT_DECO_INT.get());
            event.accept(ModBlocks.NAVIGATION_TERMINAL.get());
            event.accept(ModBlocks.SETTINGS_PANEL.get());
            event.accept(ModBlocks.BEDS.get());
            event.accept(ModBlocks.JUNCTION_BOX.get());
            event.accept(ModBlocks.SUBMARINE_BUTTON_BLOCK.get());
            event.accept(ModBlocks.SUBMARINE_DOOR.get());
            event.accept(ModBlocks.SUBMARINE_LEVER.get());
            event.accept(ModBlocks.SUBMARINE_TRAPDOOR.get());
        }

        if (event.getTab() == ModBlockGroup.BAROTRAUMA_TOOLS_TAB.get()) {
            event.accept(ModItems.NAMETAG.get());
            event.accept(ModItems.ACCESS_CONFIGURATOR.get());
            event.accept(ModItems.SLOT_LOCK_TOOL.get());
            event.accept(ModItems.GARNITURE.get());
            event.accept(ModItems.WRENCH.get());
            event.accept(ModItems.SCREWDIN.get());
            event.accept(ModItems.CROWBAR.get());
            event.accept(ModItems.OXYGEN_TANK.get());
            event.accept(ModItems.WELDING_MACHINE_FUEL_TANK.get());
            event.accept(ModItems.PANEL_CAMERA.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                BlockEntityRenderers.register(
                    ModBlockEntities.VENT_DECO.get(),
                    context -> new VentDecoRenderer()
                );

                BlockEntityRenderers.register(
                    ModBlockEntities.VENT_DECO_INT.get(),
                    context -> new VentDecoIntRenderer()
                );
                
                // Регистрируем экран для меню
                BlockEntityRenderers.register(
                    ModBlockEntities.ANIMATED_STRUCTURE_CONFIG.get(),
                    context -> new StructureConfigBlockRenderer()
                );

                MenuScreens.register(ModMenus.VENT_MENU.get(), VentScreen::new);

                BlockEntityRenderers.register(
                    ModBlockEntities.SETTINGS_PANEL.get(),
                    SettingsPanelRenderer::new
                );
            });
        }
    }
}
