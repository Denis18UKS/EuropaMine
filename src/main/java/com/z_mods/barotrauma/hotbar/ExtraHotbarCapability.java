package com.z_mods.barotrauma.hotbar;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Persistent Forge capability backing the extra hotbar without changing vanilla player inventory size. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExtraHotbarCapability {
    public static final Capability<ExtraHotbarStorage> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation ID = new ResourceLocation(Barotrauma.MOD_ID, "extra_hotbar");

    private ExtraHotbarCapability() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ExtraHotbarStorage.class);
    }

    public static ExtraHotbarStorage get(Player player) {
        return player.getCapability(CAPABILITY).orElse(null);
    }

    @Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<Entity> event) {
            if (!(event.getObject() instanceof Player)) return;
            Provider provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ExtraHotbarStorage storage = new ExtraHotbarStorage();
        private final LazyOptional<ExtraHotbarStorage> optional = LazyOptional.of(() -> storage);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return storage.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            storage.deserializeNBT(nbt);
        }

        private void invalidate() {
            optional.invalidate();
        }
    }
}
