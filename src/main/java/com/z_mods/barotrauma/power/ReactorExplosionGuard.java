package com.z_mods.barotrauma.power;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cancels the vanilla block-breaking reactor blast and replaces it with entity damage/knockback only. */
@Mod.EventBusSubscriber(modid = Barotrauma.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReactorExplosionGuard {
    private ReactorExplosionGuard() {}

    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Vec3 center = event.getExplosion().getPosition();
        BlockPos pos = BlockPos.containing(center);
        PowerWorldData data = PowerWorldData.get(level);
        PowerWorldData.MachineState state = data.machineAt(pos);
        if (state == null || !PowerWorldData.REACTOR_GUI.equals(state.guiId()) || !state.shouldExplode()) return;

        event.setCanceled(true);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y + .6D, center.z, 70, 1.4D, .8D, 1.4D, .045D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y + .5D, center.z, 36, 1.0D, .6D, 1.0D, .06D);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, .72F);
        AABB area = new AABB(pos).inflate(8.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            double distance = Math.max(.5D, entity.position().distanceTo(center));
            float damage = (float)Math.max(2.0D, 24.0D - distance * 2.5D);
            entity.hurt(level.damageSources().explosion(null, null), damage);
            Vec3 away = entity.position().subtract(center);
            if (away.lengthSqr() > 1.0E-6D) {
                Vec3 push = away.normalize().scale(Math.max(.18D, 1.65D - distance * .16D));
                entity.push(push.x, .35D, push.z);
            }
        }
    }
}
