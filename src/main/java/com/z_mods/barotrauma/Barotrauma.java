package com.z_mods.barotrauma;

import com.mojang.logging.LogUtils;
import com.z_mods.barotrauma.init.ModBlocks;
import com.z_mods.barotrauma.init.ModBlockGroup;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.init.ModBlockEntities;
import com.z_mods.barotrauma.init.ModEntities;
import com.z_mods.barotrauma.init.ModMenus;
import com.z_mods.barotrauma.blocks.StructureConfigBlockRenderer;
import com.z_mods.barotrauma.blocks.VentDecoRenderer;
import com.z_mods.barotrauma.blocks.VentDecoIntRenderer;
import com.z_mods.barotrauma.client.SettingsPanelRenderer;
import com.z_mods.barotrauma.client.HotbarLayoutPanelRenderer;
import com.z_mods.barotrauma.client.SubmarineContraptionRenderer;
import com.z_mods.barotrauma.client.VentScreen;
import com.z_mods.barotrauma.client.CabinetScreen;
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
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
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
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModNetworking.register();
        modEventBus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Entries are declared directly by ModBlockGroup to avoid duplicates.
    }

    @SubscribeEvent public void onServerStarting(ServerStartingEvent event) { }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                BlockEntityRenderers.register(ModBlockEntities.VENT_DECO.get(), context -> new VentDecoRenderer());
                BlockEntityRenderers.register(ModBlockEntities.VENT_DECO_INT.get(), context -> new VentDecoIntRenderer());
                BlockEntityRenderers.register(ModBlockEntities.ANIMATED_STRUCTURE_CONFIG.get(), context -> new StructureConfigBlockRenderer());
                MenuScreens.register(ModMenus.VENT_MENU.get(), VentScreen::new);
                MenuScreens.register(ModMenus.CABINET_MENU.get(), CabinetScreen::new);
                BlockEntityRenderers.register(ModBlockEntities.SETTINGS_PANEL.get(), SettingsPanelRenderer::new);
                BlockEntityRenderers.register(ModBlockEntities.HOTBAR_LAYOUT_PANEL.get(), HotbarLayoutPanelRenderer::new);
                EntityRenderers.register(ModEntities.SUBMARINE_CONTRAPTION.get(), SubmarineContraptionRenderer::new);
            });
        }
    }
}
