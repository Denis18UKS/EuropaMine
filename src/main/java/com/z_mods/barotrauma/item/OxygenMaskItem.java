package com.z_mods.barotrauma.item;

import com.z_mods.barotrauma.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public final class OxygenMaskItem extends ArmorItem {
    public static final String CHARGE_TAG="BarotraumaOxygenCharge"; public static final int MAX_CHARGE=24_000;
    public OxygenMaskItem(ArmorMaterial material,Properties properties){super(material,Type.HELMET,properties.stacksTo(1));}
    public static int charge(ItemStack stack){return Mth.clamp(stack.getOrCreateTag().getInt(CHARGE_TAG),0,MAX_CHARGE);} public static void setCharge(ItemStack stack,int value){stack.getOrCreateTag().putInt(CHARGE_TAG,Mth.clamp(value,0,MAX_CHARGE));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){ItemStack mask=player.getItemInHand(hand);ItemStack other=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);if(!level.isClientSide&&level instanceof ServerLevel server&&player instanceof ServerPlayer sp){if(other.is(ModItems.WELDING_MACHINE_FUEL_TANK.get())){if(!sp.getAbilities().instabuild)other.shrink(1);ToolUseEvents.safePressureExplosion(server,sp.blockPosition(),sp,"В маску установлен топливный бак вместо кислорода!");return InteractionResultHolder.fail(mask);}if(other.is(ModItems.OXYGEN_TANK.get())){if(!sp.getAbilities().instabuild)other.shrink(1);setCharge(mask,MAX_CHARGE);server.playSound(null,sp.blockPosition(),SoundEvents.BUCKET_FILL,SoundSource.PLAYERS,.8F,1.2F);sp.displayClientMessage(Component.literal("Кислородный баллон установлен в маску."),true);return InteractionResultHolder.success(mask);}}return super.use(level,player,hand);}
    @Override public void onArmorTick(ItemStack stack,Level level,Player player){if(!level.isClientSide&&player.getItemBySlot(EquipmentSlot.HEAD)==stack&&player.isInWaterOrBubble()&&charge(stack)>0){player.setAirSupply(Math.min(player.getMaxAirSupply(),player.getAirSupply()+6));if(player.tickCount%20==0)setCharge(stack,charge(stack)-20);}super.onArmorTick(stack,level,player);}
    @Override public boolean isBarVisible(ItemStack stack){return charge(stack)>0;} @Override public int getBarWidth(ItemStack stack){return Math.round(13.0F*charge(stack)/MAX_CHARGE);} @Override public int getBarColor(ItemStack stack){return 0x25E66F;}
    @Override public void appendHoverText(ItemStack stack,@Nullable Level level,List<Component> lines,TooltipFlag flag){lines.add(Component.literal("Кислородная маска с внутренним разъёмом для баллона.").withStyle(ChatFormatting.DARK_GRAY));lines.add(Component.literal("▣ Слот: кислородный баллон").withStyle(ChatFormatting.GREEN));lines.add(Component.literal("Кислород: "+Math.round(charge(stack)*100.0F/MAX_CHARGE)+"%").withStyle(ChatFormatting.GREEN));lines.add(Component.literal("Заправка: кислородный баллон во второй руке + ПКМ.").withStyle(ChatFormatting.GRAY));super.appendHoverText(stack,level,lines,flag);}
}
