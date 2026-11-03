package com.ntnh.ntnhcore;

import com.google.gson.*;
import com.tiviacz.travelersbackpack.config.TravelersBackpackConfig;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.crafting.CraftingUpgrade;
import com.tiviacz.travelersbackpack.inventory.upgrades.tanks.TanksUpgrade;
import funwayguy.betterquesting.questing.player.*;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Exercise actual native recipes, handlers, menu and BQ callbacks in the isolated instance. */
final class LegacyBackpackVerification {
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static CraftingContainer grid(){return new TransientCraftingContainer(new AbstractContainerMenu(null,0){
        @Override public boolean stillValid(Player player){return true;}
        @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    },3,3);}
    private static CraftingContainer matrix(ShapedRecipe recipe,ItemStack source){
        var matrix=grid();
        for(int i=0;i<recipe.getIngredients().size();i++){
            var ingredient=recipe.getIngredients().get(i);if(ingredient.isEmpty())continue;
            var choices=ingredient.getItems();require(choices.length>0,"Empty backpack ingredient "+recipe.getId());
            ItemStack chosen=choices[0].copy();chosen.setCount(1);
            if(!source.isEmpty()&&chosen.getItem() instanceof com.tiviacz.travelersbackpack.items.TravelersBackpackItem)chosen=source.copy();
            matrix.setItem((i/recipe.getWidth())*3+i%recipe.getWidth(),chosen);
        }
        return matrix;
    }
    static void run(MinecraftServer server,JsonObject report)throws Exception{
        require(QuestDatabase.INSTANCE.getValue(3073).getInfo().getProp(funwayguy.betterquesting.properties.CommonProps.NAME).contains("Traveler's Backpack"),"Old backpack quest title still loaded");
        require(TravelersBackpackConfig.SERVER.backpackSettings.leather.inventorySlotCount.get()==48,"Backpack server defaults did not load");
        require(!TravelersBackpackConfig.COMMON.enableVillagerTrade.get(),"Old NTNH disables backpack villager trades");
        require(!TravelersBackpackConfig.SERVER.backpackSettings.enableSleepingBagSpawnPoint.get(),"Sleeping bag must not set respawn");
        var manager=server.getRecipeManager();
        var standard=(ShapedRecipe)manager.byKey(new ResourceLocation("ntnhcore:legacy_backpack/standard")).orElseThrow();
        var fresh=standard.assemble(matrix(standard,ItemStack.EMPTY),server.registryAccess());
        require(LegacyBackpacks.legacy(fresh),"Crafted backpack is not initialized");
        var wrapper=new BackpackWrapper(fresh,0,null,server.overworld());
        require(wrapper.getStorage().getSlots()==48&&wrapper.getTools().getSlots()==2&&wrapper.getUpgrades().getSlots()==2,"Old backpack storage/tools/modules differ");
        var tanks=wrapper.getUpgrade(TanksUpgrade.class).orElseThrow();var crafting=wrapper.getUpgrade(CraftingUpgrade.class).orElseThrow();
        require(crafting.crafting.getSlots()==9&&wrapper.getSleepingBagColor()==14,"Built-in workbench or red sleeping bag missing");
        require(tanks.getLeftTank().getCapacity()==4000&&tanks.getRightTank().getCapacity()==4000,"Legacy tanks are not exactly four buckets each");
        require(tanks.getLeftTank().fill(new FluidStack(Fluids.WATER,5000),IFluidHandler.FluidAction.EXECUTE)==4000,"Left tank does not clamp to four buckets");
        require(tanks.getRightTank().fill(new FluidStack(Fluids.LAVA,4000),IFluidHandler.FluidAction.EXECUTE)==4000,"Independent right tank cannot fill");
        require(tanks.getLeftTank().drain(1000,IFluidHandler.FluidAction.EXECUTE).getAmount()==1000&&tanks.getRightTank().getFluidAmount()==4000,"Tanks drain together or lose fluid");
        wrapper.getStorage().setStackInSlot(47,new ItemStack(Items.DIAMOND,23));
        var tool=new ItemStack(Items.IRON_PICKAXE);tool.setDamageValue(17);wrapper.getTools().setStackInSlot(1,tool);
        crafting.crafting.setStackInSlot(8,new ItemStack(Items.OAK_PLANKS,11));fresh.setHoverName(net.minecraft.network.chat.Component.literal("Проверка рюкзака"));
        var serialized=fresh.save(new CompoundTag());var restored=ItemStack.of(serialized);var loaded=new BackpackWrapper(restored,0,null,server.overworld());
        require(loaded.getStorage().getStackInSlot(47).getCount()==23&&loaded.getTools().getStackInSlot(1).getDamageValue()==17,"Storage/tools lost after native NBT roundtrip");
        var loadedTanks=loaded.getUpgrade(TanksUpgrade.class).orElseThrow();
        require(loadedTanks.getLeftTank().getFluidAmount()==3000&&loadedTanks.getRightTank().getFluidAmount()==4000,"Native tank NBT roundtrip lost fluids");
        require(loaded.getUpgrade(CraftingUpgrade.class).orElseThrow().crafting.getStackInSlot(8).getCount()==11,"Workbench NBT roundtrip lost items");
        JsonObject source;
        try(var input=LegacyBackpacks.class.getResourceAsStream("/ntnhcore/legacy-backpack-source.json")){
            source=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
        int checked=0,skins=0;
        for(var row:source.getAsJsonObject("recipes").entrySet()){
            var recipe=(ShapedRecipe)manager.byKey(new ResourceLocation(row.getValue().getAsString())).orElseThrow();var matrix=matrix(recipe,fresh);
            var selected=manager.getRecipeFor(RecipeType.CRAFTING,matrix,server.overworld()).orElseThrow();
            require(selected.getId().equals(recipe.getId()),"Backpack recipe conflicts with "+selected.getId()+" for "+recipe.getId());
            var output=selected.assemble(matrix,server.registryAccess());require(!output.isEmpty(),"Empty native craft "+recipe.getId());checked++;
            if(recipe instanceof LegacyBackpacks.Recipe&&!row.getKey().equals("standard")){
                var expected=fresh.getTag().copy();var template=recipe.getResultItem(server.registryAccess());if(template.hasTag())expected.merge(template.getTag());
                require(expected.equals(output.getTag()),"Reskin loses or rewrites NBT: "+row.getKey());skins++;
                var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try{
                    @SuppressWarnings("unchecked") var serializer=(RecipeSerializer<LegacyBackpacks.Recipe>)recipe.getSerializer();
                    serializer.toNetwork(buffer,(LegacyBackpacks.Recipe)recipe);
                    var decoded=serializer.fromNetwork(recipe.getId(),buffer);require(ItemStack.matches(output,decoded.assemble(matrix,server.registryAccess())),"Recipe packet loses NBT/color");
                }finally{buffer.release();}
            }
            if(row.getKey().equals("dragon")){
                int eggs=selected.getRemainingItems(matrix).stream().filter(s->s.is(Items.DRAGON_EGG)).mapToInt(ItemStack::getCount).sum();
                require(eggs==1,"Dragon reskin consumes the old reusable dragon egg");
            }
        }
        for(var id:source.getAsJsonArray("native_recipes_disabled"))require(manager.byKey(new ResourceLocation(id.getAsString())).isEmpty(),"Modern backpack recipe bypass remains: "+id);
        var lootBag=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("travelersbackpack:bat")));
        var lootWrapper=new BackpackWrapper(lootBag,0,null,server.overworld());
        require(LegacyBackpacks.legacy(lootBag)&&lootWrapper.getStorage().getSlots()==48&&lootWrapper.getUpgrade(CraftingUpgrade.class).isPresent()&&lootWrapper.getUpgrade(TanksUpgrade.class).orElseThrow().getLeftTank().getCapacity()==4000,"Fresh found backpack misses built-in functions");
        var existing=new ItemStack(fresh.getItem());existing.getOrCreateTag().putInt("StorageSlots",27);existing.getOrCreateTag().putInt("UpgradeSlots",2);existing.getOrCreateTag().putInt("ToolSlots",2);
        var preserved=new BackpackWrapper(existing,0,null,server.overworld());require(preserved.getStorage().getSlots()==27&&!LegacyBackpacks.legacy(existing),"Existing initialized modern backpack was rewritten");
        var actor=server.getPlayerList().getPlayerByName("NTNHNovTest");require(actor!=null,"Connected isolated client required for backpack menu");
        var hand=actor.getMainHandItem().copy();boolean shift=actor.isShiftKeyDown();
        try{
            actor.setShiftKeyDown(false);actor.setItemInHand(InteractionHand.MAIN_HAND,restored.copy());
            var response=restored.getItem().use(server.overworld(),actor,InteractionHand.MAIN_HAND);
            require(response.getResult().consumesAction()&&actor.containerMenu.getClass().getName().startsWith("com.tiviacz.travelersbackpack"),"Native backpack menu does not open");
            actor.closeContainer();
        }finally{actor.closeContainer();actor.setShiftKeyDown(shift);actor.setItemInHand(InteractionHand.MAIN_HAND,hand);}
        quest(server,report,fresh,lootBag);
        report.addProperty("legacy_backpack_real_recipes_checked",checked);report.addProperty("legacy_backpack_reskins_preserve_nbt_and_recipe_packets",skins);
        report.addProperty("legacy_backpack_storage_slots",48);report.addProperty("legacy_backpack_tool_slots",2);report.addProperty("legacy_backpack_each_tank_mb",4000);
        report.addProperty("legacy_backpack_native_nbt_roundtrip_and_independent_fluids",true);report.addProperty("legacy_backpack_native_menu_opened",true);
        report.addProperty("legacy_backpack_fresh_loot_and_existing_modern_bag_checked",true);report.addProperty("legacy_backpack_extra_independent_crafting_slots_difference",9);
    }
    private static void quest(MinecraftServer server,JsonObject report,ItemStack standard,ItemStack variant){
        var player=server.getPlayerList().getPlayerByName("NTNHNovTest");require(player!=null,"Connected isolated player required for custom inventory detection");
        var participant=new ParticipantInfo(player.getUUID());
        var progress=(funwayguy.xbt.std.XBTTagCompound)participant.PLAYER.progress.writeToXBT(new funwayguy.xbt.std.XBTTagCompound()).copy();
        var inventory=player.getInventory().save(new net.minecraft.nbt.ListTag());int selected=player.getInventory().selected;
        try{
        participant.PLAYER.progress.clearTaskData(3073,257);player.getInventory().clearContent();
        var task=(BQQuestTasks.Inventory)QuestDatabase.INSTANCE.getValue(3073).getTasks().getValue(257);
        task.detect(participant,257,3073);require(!participant.PLAYER.progress.getTaskData(3073,257).getBoolean("isComplete"),"Empty backpack quest completes");
        player.getInventory().add(new ItemStack(Items.CHEST));task.detect(participant,257,3073);require(!participant.PLAYER.progress.getTaskData(3073,257).getBoolean("isComplete"),"Chest completes backpack quest");
        player.getInventory().add(variant.copy());task.detect(participant,257,3073);
        require(participant.PLAYER.progress.getTaskData(3073,257).getBoolean("isComplete")&&player.getInventory().countItem(variant.getItem())==1,"Reskinned backpack fails nonconsuming BQ callback");
        participant.PLAYER.progress.setTaskDone(3073,257,false);player.getInventory().clearContent();player.getInventory().add(standard.copy());task.detect(participant,257,3073);
        require(participant.PLAYER.progress.getTaskData(3073,257).getBoolean("isComplete")&&player.getInventory().countItem(standard.getItem())==1,"Named/filled standard backpack fails BQ callback");
        participant.PLAYER.progress.clearTaskData(3073,513);
        var compass=(funwayguy.bq_standard.questing.tasks.TaskRetrieval)QuestDatabase.INSTANCE.getValue(3073).getTasks().getValue(513);
        compass.onInventoryChange(participant,513,3073,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(player.getInventory()));compass.detect(participant,513,3073);
        require(!participant.PLAYER.progress.getTaskData(3073,513).getBoolean("isComplete"),"Backpack alone also completes compass condition");
        var compassItem=BuiltInRegistries.ITEM.get(new ResourceLocation("naturescompass:naturescompass"));player.getInventory().add(new ItemStack(compassItem));
        compass.onInventoryChange(participant,513,3073,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(player.getInventory()));compass.detect(participant,513,3073);
        require(participant.PLAYER.progress.getTaskData(3073,513).getBoolean("isComplete")&&player.getInventory().countItem(compassItem)==1,"Compass fails inventory callback or is consumed");
        report.addProperty("legacy_backpack_quest_empty_wrong_variant_and_named_nonconsuming_checked",true);
        report.addProperty("legacy_backpack_compass_required_and_nonconsuming_checked",true);
        }finally{participant.PLAYER.progress.clearAll();participant.PLAYER.progress.readFromXBT(progress);participant.markDirty(3073);player.getInventory().clearContent();player.getInventory().load(inventory);player.getInventory().selected=selected;player.inventoryMenu.broadcastChanges();}
    }
}
