package com.ntnh.ntnhcore;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;

/** NTNH's early-game axe requirement applies to logs, never planks or leaves. */
final class LegacyWoodHarvest {
    static boolean allowed(BlockState state,ItemStack tool){
        return !state.is(BlockTags.LOGS)||tool.is(ItemTags.AXES)||tool.canPerformAction(ToolActions.AXE_DIG);
    }
    private static boolean blocked(Player player,BlockState state){return !player.isCreative()&&!player.isSpectator()&&!allowed(state,player.getMainHandItem());}
    static void speed(PlayerEvent.BreakSpeed event){if(blocked(event.getEntity(),event.getState())){event.setNewSpeed(0);event.setCanceled(true);}}
    static void breaking(BlockEvent.BreakEvent event){if(blocked(event.getPlayer(),event.getState()))event.setCanceled(true);}
}
