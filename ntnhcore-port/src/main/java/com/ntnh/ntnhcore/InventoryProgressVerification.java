package com.ntnh.ntnhcore;

import com.google.gson.*;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.bootstrap.forge.events.InvoProxy;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Real BQ matching and Forge event checks, restricted to the isolated validation server. */
final class InventoryProgressVerification {
    static void run(MinecraftServer server,JsonObject report,JsonArray errors){
        try{
            ServerPlayer player=new ServerPlayer(server,server.overworld(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"InventoryCheck")){
                @Override public boolean isCreative(){return getAbilities().instabuild;}
            };
            ProfileManager.get().makeOrUpdate(player.getUUID(),"InventoryCheck");var participant=new ParticipantInfo(player.getUUID());
            var flint=(TaskRetrieval)QuestDatabase.INSTANCE.getValue(10241).getTasks().getValue(257);
            var pick=new ItemStack(LegacyFlintTools.pickaxe.get());pick.setDamageValue(7);
            var axe=new ItemStack(LegacyFlintTools.hatchet.get());axe.setDamageValue(9);
            player.getInventory().add(pick.copy());update(player,participant,flint,10241,257);
            boolean partial=!participant.PLAYER.progress.getTaskData(10241,257).getBoolean("isComplete");
            player.getInventory().add(axe.copy());update(player,participant,flint,10241,257);
            boolean tools=participant.PLAYER.progress.getTaskData(10241,257).getBoolean("isComplete")&&player.getInventory().countItem(pick.getItem())==1&&player.getInventory().countItem(axe.getItem())==1;
            var maskQuest=QuestDatabase.INSTANCE.getValue(23297);
            var entry=maskQuest.getTasks().getEntries().stream().filter(t->t.getValue() instanceof TaskRetrieval).findFirst().orElseThrow();
            var task=(TaskRetrieval)entry.getValue();player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.LEATHER_HELMET));update(player,participant,task,23297,entry.getID());
            boolean wrong=!participant.PLAYER.progress.getTaskData(23297,entry.getID()).getBoolean("isComplete");
            player.getInventory().clearContent();var mask=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("hbm:mask_piss")));
            mask.setDamageValue(12);mask.setHoverName(net.minecraft.network.chat.Component.literal("Проверка маски"));player.getInventory().armor.set(3,mask);
            update(player,participant,task,23297,entry.getID());
            boolean worn=participant.PLAYER.progress.getTaskData(23297,entry.getID()).getBoolean("isComplete")&&player.getInventory().armor.get(3)==mask;
            player.getAbilities().instabuild=false;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var pos=server.overworld().getSharedSpawnPos();var log=Blocks.OAK_LOG.defaultBlockState();
            var punch=new net.minecraftforge.event.level.BlockEvent.BreakEvent(server.overworld(),pos,log,player);LegacyWoodHarvest.breaking(punch);
            var speed=new net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed(player,log,2f,pos);LegacyWoodHarvest.speed(speed);
            boolean wood=punch.isCanceled()&&speed.isCanceled()&&speed.getNewSpeed()==0&&!LegacyWoodHarvest.allowed(log,new ItemStack(Items.STICK));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,axe);
            var chop=new net.minecraftforge.event.level.BlockEvent.BreakEvent(server.overworld(),pos,log,player);LegacyWoodHarvest.breaking(chop);
            wood&=!chop.isCanceled()&&LegacyWoodHarvest.allowed(Blocks.OAK_PLANKS.defaultBlockState(),ItemStack.EMPTY)&&LegacyWoodHarvest.allowed(Blocks.OAK_LEAVES.defaultBlockState(),ItemStack.EMPTY);
            player.getAbilities().instabuild=true;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            var creative=new net.minecraftforge.event.level.BlockEvent.BreakEvent(server.overworld(),pos,log,player);LegacyWoodHarvest.breaking(creative);wood&=!creative.isCanceled();
            report.addProperty("retrieval_damaged_flint_tools_accepted_not_consumed",tools);report.addProperty("retrieval_partial_tools_rejected",partial);
            report.addProperty("retrieval_wrong_mask_rejected",wrong);report.addProperty("retrieval_worn_named_mask_in_armor_accepted",worn);
            report.addProperty("wood_hand_blocked_axes_allowed_planks_leaves_creative_unchanged",wood);
            if(!tools||!partial||!wrong||!worn||!wood)errors.add("Inventory or early wood progression checks failed");
        }catch(Exception error){errors.add("Inventory/wood verification: "+error);org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Inventory verification fixture failed",error);}
    }
    private static void update(ServerPlayer player,ParticipantInfo participant,TaskRetrieval task,int quest,int id){
        task.onInventoryChange(participant,id,quest,new InvoProxy(player.getInventory()));task.detect(participant,id,quest);
    }
}
