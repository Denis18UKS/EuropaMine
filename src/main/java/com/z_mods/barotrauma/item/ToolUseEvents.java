package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.Barotrauma;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=Barotrauma.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ToolUseEvents {
    private ToolUseEvents(){}
    @SubscribeEvent public static void rightClickItem(PlayerInteractEvent.RightClickItem e){blockOffhand(e);}
    @SubscribeEvent public static void rightClickBlock(PlayerInteractEvent.RightClickBlock e){blockOffhand(e);}
    @SubscribeEvent public static void entityInteract(PlayerInteractEvent.EntityInteract e){blockOffhand(e);}
    private static void blockOffhand(PlayerInteractEvent event){if(event.getHand()!=InteractionHand.OFF_HAND)return;ItemStack main=event.getEntity().getMainHandItem();if(!(main.getItem() instanceof TwoHandedItem))return;event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);if(!event.getLevel().isClientSide)event.getEntity().displayClientMessage(Component.literal("Этот аппарат занимает обе руки."),true);}
    public static void safePressureExplosion(ServerLevel level,BlockPos pos,ServerPlayer source,String message){level.sendParticles(ParticleTypes.LARGE_SMOKE,pos.getX()+.5D,pos.getY()+1D,pos.getZ()+.5D,26,.8D,.5D,.8D,.035D);level.sendParticles(ParticleTypes.FLAME,pos.getX()+.5D,pos.getY()+.8D,pos.getZ()+.5D,18,.6D,.35D,.6D,.04D);level.playSound(null,pos,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.2F,1.05F);AABB box=new AABB(pos).inflate(4.5D);Vec3 center=Vec3.atCenterOf(pos);for(LivingEntity entity:level.getEntitiesOfClass(LivingEntity.class,box)){double distance=Math.max(.5D,entity.position().distanceTo(center));float damage=(float)Math.max(2D,14D-distance*2D);entity.hurt(level.damageSources().generic(),damage);Vec3 delta=entity.position().subtract(center);if(delta.lengthSqr()>1.0E-6D){Vec3 push=delta.normalize().scale(Math.max(.15D,1.25D-distance*.15D));entity.push(push.x,.28D,push.z);}}source.displayClientMessage(Component.literal(message),false);}
}
