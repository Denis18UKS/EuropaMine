package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class ChargedSupplyItem extends Item implements TwoHandedItem {
    public enum SupplyKind { WELDING_FUEL, OXYGEN }
    public static final String CHARGE_TAG = "BarotraumaSupplyCharge";
    public static final int MAX_CHARGE = 24_000;
    private final SupplyKind supplyKind; private final String description; private final String skill;
    public ChargedSupplyItem(SupplyKind supplyKind, String description, String skill, Properties properties) { super(properties.stacksTo(1)); this.supplyKind=supplyKind; this.description=description; this.skill=skill; }
    public SupplyKind supplyKind(){return supplyKind;}
    public static int charge(ItemStack stack){ return Mth.clamp(stack.getOrCreateTag().getInt(CHARGE_TAG),0,MAX_CHARGE); }
    public static void setCharge(ItemStack stack,int value){ stack.getOrCreateTag().putInt(CHARGE_TAG,Mth.clamp(value,0,MAX_CHARGE)); }
    private boolean correctTank(ItemStack stack){ return supplyKind==SupplyKind.WELDING_FUEL ? stack.is(ModItems.WELDING_MACHINE_FUEL_TANK.get()) : stack.is(ModItems.OXYGEN_TANK.get()); }
    private boolean wrongTank(ItemStack stack){ return supplyKind==SupplyKind.WELDING_FUEL ? stack.is(ModItems.OXYGEN_TANK.get()) : stack.is(ModItems.WELDING_MACHINE_FUEL_TANK.get()); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand){
        ItemStack tool=player.getItemInHand(hand); ItemStack other=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
        if(!level.isClientSide && level instanceof ServerLevel server && player instanceof ServerPlayer sp){
            if(wrongTank(other)){ if(!sp.getAbilities().instabuild) other.shrink(1); ToolUseEvents.safePressureExplosion(server,sp.blockPosition(),sp,"Неверный баллон: произошла аварийная реакция!"); return InteractionResultHolder.fail(tool); }
            if(correctTank(other)){ if(charge(tool)>=MAX_CHARGE){sp.displayClientMessage(Component.literal("Аппарат уже полностью заправлен."),true);return InteractionResultHolder.success(tool);} if(!sp.getAbilities().instabuild) other.shrink(1); setCharge(tool,MAX_CHARGE); server.playSound(null,sp.blockPosition(),SoundEvents.BUCKET_FILL,SoundSource.PLAYERS,.8F,1.15F); sp.displayClientMessage(Component.literal("Баллон установлен. Заряд: 100%."),true); return InteractionResultHolder.success(tool); }
        }
        return InteractionResultHolder.pass(tool);
    }
    @Override public InteractionResult useOn(UseOnContext context){ ItemStack stack=context.getItemInHand(); if(charge(stack)<=0){if(!context.getLevel().isClientSide&&context.getPlayer()!=null)context.getPlayer().displayClientMessage(Component.literal("Аппарат разряжен: установите подходящий баллон."),true);return InteractionResult.FAIL;} if(!context.getLevel().isClientSide&&context.getLevel() instanceof ServerLevel server){setCharge(stack,charge(stack)-40);server.sendParticles(supplyKind==SupplyKind.WELDING_FUEL?ParticleTypes.FLAME:ParticleTypes.CLOUD,context.getClickLocation().x,context.getClickLocation().y,context.getClickLocation().z,5,.06D,.06D,.06D,.005D);server.playSound(null,context.getClickedPos(),supplyKind==SupplyKind.WELDING_FUEL?SoundEvents.FIRE_EXTINGUISH:SoundEvents.IRON_TRAPDOOR_OPEN,SoundSource.PLAYERS,.45F,supplyKind==SupplyKind.WELDING_FUEL?1.7F:1.2F);} return InteractionResult.sidedSuccess(context.getLevel().isClientSide); }
    @Override public boolean isBarVisible(ItemStack stack){return charge(stack)>0;}
    @Override public int getBarWidth(ItemStack stack){return Math.round(13.0F*charge(stack)/MAX_CHARGE);}
    @Override public int getBarColor(ItemStack stack){return 0x25E66F;}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.literal("Обычное качество").withStyle(ChatFormatting.GRAY));lines.add(Component.literal(description).withStyle(ChatFormatting.DARK_GRAY));lines.add(Component.literal("Работает даже под водой.").withStyle(ChatFormatting.DARK_GRAY));lines.add(Component.empty());lines.add(Component.literal("НЕОБХОДИМЫЕ НАВЫКИ:").withStyle(ChatFormatting.GOLD));lines.add(Component.literal(skill).withStyle(ChatFormatting.GRAY));lines.add(Component.empty());String slot=supplyKind==SupplyKind.WELDING_FUEL?"топливный бак":"кислородный баллон";lines.add(Component.literal("▣ Слот: "+slot).withStyle(ChatFormatting.GREEN));lines.add(Component.literal("Заряд: "+Math.round(charge(stack)*100.0F/MAX_CHARGE)+"%").withStyle(ChatFormatting.GREEN));lines.add(Component.literal("Для заправки: держите баллон во второй руке и нажмите ПКМ.").withStyle(ChatFormatting.DARK_GRAY));super.appendHoverText(stack,level,lines,flag);}
}
