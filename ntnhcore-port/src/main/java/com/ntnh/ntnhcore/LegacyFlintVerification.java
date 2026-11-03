package com.ntnh.ntnhcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

final class LegacyFlintVerification {
    static void run(MinecraftServer server,JsonObject report,JsonArray errors){
        boolean crafted=true;
        String[] names={"flint_pickaxe","flint_shovel","flint_hatchet"};
        Item[] tools={LegacyFlintTools.pickaxe.get(),LegacyFlintTools.shovel.get(),LegacyFlintTools.hatchet.get()};
        Item[][] inputs={{Items.FLINT,Items.FLINT,Items.STICK,Items.AIR},
            {Items.FLINT,Items.AIR,Items.STICK,Items.AIR},{Items.AIR,Items.FLINT,Items.STICK,Items.AIR}};
        for(int i=0;i<names.length;i++){
            var recipe=server.getRecipeManager().byKey(new ResourceLocation("ntnhcore",names[i])).orElseThrow();
            var menu=new net.minecraft.world.inventory.AbstractContainerMenu(null,0){
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return true;}
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player,int slot){return ItemStack.EMPTY;}
            };
            CraftingContainer grid=new TransientCraftingContainer(menu,2,2);
            for(int slot=0;slot<4;slot++)if(inputs[i][slot]!=Items.AIR)grid.setItem(slot,new ItemStack(inputs[i][slot]));
            crafted&=recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting
                && crafting.matches(grid,server.overworld())&&crafting.assemble(grid,server.registryAccess()).is(tools[i]);
        }
        ItemStack pick=new ItemStack(tools[0]);
        boolean tier=pick.getMaxDamage()==59&&pick.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())
            &&!pick.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())
            &&pick.getDestroySpeed(Blocks.STONE.defaultBlockState())==3.0f
            &&tools[0].isValidRepairItem(pick,new ItemStack(Items.FLINT));
        var quest=QuestDatabase.INSTANCE.getValue(10241);
        boolean taskItems=quest!=null&&quest.getTasks().size()==2;
        if(taskItems)for(var entry:quest.getTasks().getEntries()){
            if(entry.getID()==513){taskItems&=entry.getValue() instanceof OptionalInventoryObjective;continue;}
            if(!(entry.getValue() instanceof TaskRetrieval task)||task.requiredItems.size()!=2){taskItems=false;break;}
            var actual=new java.util.HashSet<Item>();
            for(var required:task.requiredItems){
                var stack=funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry.convertStack(required);
                actual.add(stack.getItem());taskItems&=stack.getCount()==1;
            }
            taskItems&=actual.equals(java.util.Set.of(tools[0],tools[2]))&&!task.consume&&task.ignoreNBT;
        }
        var player=new net.minecraft.server.level.ServerPlayer(server,server.overworld(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"NTNHFlintCheck"));
        var progress=new funwayguy.betterquesting.questing.player.progress.PlayerProgress(player.getUUID());
        var optionalTask=(OptionalInventoryObjective)quest.getTasks().getValue(513);
        optionalTask.update(player,progress,10241,513);
        boolean optional=progress.getTaskData(10241,513).getBoolean("isComplete")
            &&!progress.getTaskData(10241,513).getBoolean("optional_acquired");
        player.getInventory().add(new ItemStack(tools[1]));
        optionalTask.update(player,progress,10241,513);
        optional&=progress.getTaskData(10241,513).getBoolean("optional_acquired")&&player.getInventory().countItem(tools[1])==1;
        report.addProperty("flint_shovel_is_optional_and_not_consumed",optional);
        report.addProperty("flint_original_2x2_recipes",crafted);
        report.addProperty("flint_original_tier_and_repair",tier);
        report.addProperty("flint_quest_restored",taskItems);
        if(!crafted||!tier||!taskItems||!optional)errors.add("Restored flint progression mismatch");
    }
}
