package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.bq_standard.bootstrap.forge.events.InvoProxy;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.UUID;

/** Tests actual successor-mod menus, not a second implementation of its cutter. */
final class ArchitectureVerification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void run(MinecraftServer server,JsonObject report)throws Exception{
        var player=new ServerPlayer(server,server.overworld(),new GameProfile(UUID.randomUUID(),"ArchitectureCheck"));
        var type=Class.forName("com.summax.apn.architecture.common.block.container.ContainerSawbench");
        var pageType=Class.forName("com.summax.apn.architecture.common.shape.ShapePage");
        var pages=(List<?>)pageType.getField("PAGES").get(null);
        int checked=0;var unique=new java.util.HashSet<String>();
        for(int page=0;page<pages.size();page++)for(int shape=0;shape<((Number)pageType.getMethod("size").invoke(pages.get(page))).intValue();shape++){
            var menu=(AbstractContainerMenu)type.getConstructor(Inventory.class,int.class,BlockPos.class).newInstance(player.getInventory(),1,null);
            player.getInventory().clearContent();menu.getSlot(0).set(new ItemStack(Items.STONE,64));
            type.getMethod("selectShape",int.class,int.class).invoke(menu,page,shape);
            int used=((Number)type.getMethod("getMaterialMultiple").invoke(menu)).intValue();
            int amount=((Number)type.getMethod("getResultMultiple").invoke(menu)).intValue();
            require(used>0&&used<=64&&amount>0,"Invalid APN material cost");
            var output=menu.getSlot(1).getItem().copy();require(!output.isEmpty()&&output.getCount()==amount,"Sawbench has no result for "+page+"/"+shape);
            unique.add(output.getItem()+" "+output.getTag());
            var saved=output.save(new net.minecraft.nbt.CompoundTag());require(ItemStack.matches(output,ItemStack.of(saved)),"Architectural shape loses material/shape in NBT");
            var moved=menu.quickMoveStack(player,1);require(!moved.isEmpty(),"Sawbench shift-click fails");
            require(menu.getSlot(0).getItem().getCount()==64-used,"Sawbench consumes wrong material amount");
            require(player.getInventory().items.stream().filter(s->ItemStack.isSameItemSameTags(s,output)).mapToInt(ItemStack::getCount).sum()==amount,"Sawbench output duplicated or lost");
            menu.getSlot(0).set(new ItemStack(Items.STICK));require(menu.getSlot(1).getItem().isEmpty(),"Sawbench accepts a stick as a block material");checked++;
        }
        var menu=(AbstractContainerMenu)type.getConstructor(Inventory.class,int.class,BlockPos.class).newInstance(player.getInventory(),1,null);
        require(!menu.clickMenuButton(player,-1)&&!menu.clickMenuButton(player,9999),"Sawbench accepts invalid page buttons");
        var task=(TaskRetrieval)QuestDatabase.INSTANCE.getValue(91905).getTasks().getValue(257);
        ProfileManager.get().makeOrUpdate(player.getUUID(),"ArchitectureCheck");var participant=new ParticipantInfo(player.getUUID());player.getInventory().clearContent();
        task.onInventoryChange(participant,257,91905,new InvoProxy(player.getInventory()));task.detect(participant,257,91905);
        require(!participant.PLAYER.progress.getTaskData(91905,257).getBoolean("isComplete"),"Architecture quest completes with empty inventory");
        var saw=BuiltInRegistries.ITEM.getOptional(new ResourceLocation("apn:sawbench")).orElseThrow();
        player.getInventory().add(new ItemStack(saw));task.onInventoryChange(participant,257,91905,new InvoProxy(player.getInventory()));task.detect(participant,257,91905);
        require(participant.PLAYER.progress.getTaskData(91905,257).getBoolean("isComplete"),"Sawbench not detected by restored quest");
        require(player.getInventory().countItem(saw)==1,"Architecture detection consumes the sawbench");
        require(unique.size()>=90,"Architecture successor is missing expected shape variants");
        report.addProperty("architecture_sawbench_shapes_cut_and_shift_clicked",checked);
        report.addProperty("architecture_unique_shapes_checked",unique.size());
        report.addProperty("architecture_shape_nbt_and_material_costs_checked",true);
        report.addProperty("architecture_quest_detects_sawbench_without_consuming",true);
    }
}
