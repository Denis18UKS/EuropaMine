package com.z_mods.barotrauma;

import com.mojang.logging.LogUtils;
import com.z_mods.barotrauma.init.ModBlocks;
import com.z_mods.barotrauma.init.ModBlockGroup;
import com.z_mods.barotrauma.init.ModItems;
import com.z_mods.barotrauma.init.ModBlockEntities;
import com.z_mods.barotrauma.blocks.VentDecoRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod(Barotrauma.MOD_ID)
public class Barotrauma {
    public static final String MOD_ID = "barotrauma";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Barotrauma() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Инициализируем GeckoLib
        GeckoLib.initialize();

        // Регистрируем все DeferredRegister
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockGroup.CREATIVE_MODE_TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModBlockGroup.BAROTRAUMA_TAB.get()) {
            event.accept(ModBlocks.VENT_DECO.get());
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
                // Регистрируем GeckoLib рендерер
                BlockEntityRenderers.register(
                    ModBlockEntities.VENT_DECO.get(),
                    context -> new VentDecoRenderer()
                );
            });
        }
    }
}