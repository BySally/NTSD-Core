package com.ntnh.ntnhcore;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry;
import funwayguy.betterquesting.questing.player.progress.PlayerProgress;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Integration checks in an explicitly enabled isolated validation server, never automatic on login. */
final class ContentVerification {
    interface Check {void run()throws Exception;}
    static void run(MinecraftServer server,JsonObject report,JsonArray errors){
        section("optional",errors,()->optional(server,report));
        section("crafting",errors,()->crafting(server,report));
        section("legacy_ae2",errors,()->LegacyAE2Verification.run(server,report));
        section("legacy_energy",errors,()->LegacyEnergyVerification.run(server,report));
        section("legacy_chisel",errors,()->LegacyChiselVerification.run(server,report));
        section("legacy_drawer_tape",errors,()->LegacyDrawerTapeVerification.run(server,report));
        section("legacy_wireless",errors,()->LegacyWirelessVerification.run(server,report));
        section("legacy_backpack",errors,()->LegacyBackpackVerification.run(server,report));
        section("legacy_radial",errors,()->LegacyRadialVerification.run(server,report));
        section("legacy_rf_converter",errors,()->LegacyRfEnergyVerification.run(server,report));
        section("hbm_ore_cleanup",errors,()->LegacyHbmOreVerification.run(server,report));
        section("architecture",errors,()->ArchitectureVerification.run(server,report));
        section("markings",errors,()->MarkingsVerification.run(server,report));
        section("loot",errors,()->loot(server,report));
        section("moon",errors,()->moon(server,report));
    }
    private static void section(String name,JsonArray errors,Check action){
        try{action.run();}catch(Exception|LinkageError e){errors.add(name+": "+e);org.slf4j.LoggerFactory.getLogger("ntnhcore").error("Integration check "+name,e);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static ServerPlayer player(MinecraftServer server,String name){return new ServerPlayer(server,server.overworld(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
    private static void optional(MinecraftServer server,JsonObject report){
        int mapped=0,unavailable=0,fluidChecks=0;
        for(var quest:QuestDatabase.INSTANCE.getEntries())for(var entry:quest.getValue().getTasks().getEntries()){
            if(!(entry.getValue() instanceof OptionalInventoryObjective task))continue;
            var player=player(server,"OptionalCheck");var progress=new PlayerProgress(player.getUUID());int q=quest.getID(),t=entry.getID();
            task.update(player,progress,q,t);
            boolean neutral=quest.getValue().getInfo().getProp(funwayguy.betterquesting.properties.CommonProps.LOGIC_TASK)!=funwayguy.betterquesting.enums.ELogic.OR;
            require(progress.getTaskData(q,t).getBoolean("isComplete")==neutral,"Optional objective changes required progression "+q);
            require(!progress.getTaskData(q,t).getBoolean("optional_acquired"),"Empty inventory grants optional acquisition "+q);
            if(!task.unavailable.isEmpty()){unavailable++;continue;}
            if(task.itemConstraints.size()>0){
                // A legacy full counter must not validate a modern empty cell.
                var counters=new int[task.requiredItems.size()];Arrays.fill(counters,Integer.MAX_VALUE);
                progress.getTaskData(q,t).setIntArray("counters",counters);
                for(var wanted:task.requiredItems){var stack=ItemRegistry.convertStack(wanted);player.getInventory().add(stack.copy());}
                task.update(player,progress,q,t);
                require(!progress.getTaskData(q,t).getBoolean("optional_acquired"),"Empty cell or old counter bypasses SAS3 requirement");
                player.getInventory().clearContent();fluidChecks++;
            }
            int total=0;
            for(int i=0;i<task.requiredItems.size();i++){
                // BQ's converter clamps a display stack to the item's stack limit.
                // The objective count may be larger (for example three unstackable electrodes).
                var stack=ItemRegistry.convertStack(task.displayItem(i));int required=task.requiredItems.get(i).count;total+=required;
                int remaining=required;
                while(remaining>0){var part=stack.copy();int count=Math.min(remaining,part.getMaxStackSize());part.setCount(count);
                    require(player.getInventory().add(part),"Optional inventory fixture too large "+q);remaining-=count;}
            }
            task.update(player,progress,q,t);
            require(progress.getTaskData(q,t).getBoolean("optional_acquired"),"Correct items fail optional objective "+q);
            require(player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum()==total,"Optional objective consumes items "+q);
            player.getInventory().clearContent();task.update(player,progress,q,t);
            require(progress.getTaskData(q,t).getBoolean("optional_acquired"),"Optional acquisition lost after inventory change "+q);
            mapped++;
        }
        var sas=new JsonObject();sas.addProperty("cell_fluid","SAS3");sas.addProperty("amount",1000);
        var water=sas.deepCopy();water.addProperty("cell_fluid","WATER");
        require(HbmQuestRequirement.matches(sas,HbmQuestRequirement.display(sas)),"Full SAS3 cell rejected");
        require(!HbmQuestRequirement.matches(sas,HbmQuestRequirement.display(water)),"Wrong cell fluid accepted");
        report.addProperty("mapped_optional_goals_checked",mapped);report.addProperty("unavailable_optional_goals_nonblocking",unavailable);
        report.addProperty("sas3_empty_wrong_fluid_and_stale_counter_rejected",fluidChecks==1);
        require(mapped==67&&unavailable==6&&fluidChecks==1,"Optional goal counts differ");
    }
    private static CraftingContainer grid(){return new TransientCraftingContainer(new AbstractContainerMenu(null,0){
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
        @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int slot){return ItemStack.EMPTY;}
    },3,3);}
    private static void crafting(MinecraftServer server,JsonObject report){
        String[] ids={"migrated/bibliocraft/002","migrated/bibliocraft/006","migrated/minecraft/013","migrated/ntm/004","migrated/ntm/007"};
        int checked=0;
        for(var id:ids){
            var recipe=(CraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("ntnhcore",id)).orElseThrow();
            var grid=grid();int width=recipe instanceof ShapedRecipe shape?shape.getWidth():3;
            for(int i=0;i<recipe.getIngredients().size();i++){
                var variants=recipe.getIngredients().get(i).getItems();if(variants.length>0)grid.setItem((i/width)*3+i%width,variants[0].copy());
            }
            require(recipe.matches(grid,server.overworld()),"New recipe rejects its ingredients "+id);
            var result=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).orElseThrow();
            require(ItemStack.matches(result.assemble(grid,server.registryAccess()),recipe.assemble(grid,server.registryAccess())),"Conflicting recipe wins "+id+": "+result.getId());checked++;
        }
        var pan=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("pamhc2foodcore:saucepanitem")));
        for(String cheese:List.of("hbm:cheese","pamhc2foodcore:cheeseitem"))for(String butter:List.of("ntmspace:butter","pamhc2foodcore:butteritem")){
            var grid=grid();grid.setItem(0,pan.copy());grid.setItem(1,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(cheese))));
            grid.setItem(2,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("pamhc2foodextended:tortillaitem"))));
            grid.setItem(3,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(butter))));
            var recipe=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).orElseThrow();
            var result=recipe.assemble(grid,server.registryAccess());require(BuiltInRegistries.ITEM.getKey(result.getItem()).toString().equals("hbm:cheese_quesadilla")&&result.getCount()==1,"Food tags bypass NTNH quesadilla recipe");
            require(recipe.getRemainingItems(grid).stream().anyMatch(s->s.is(pan.getItem())),"Saucepan is consumed by the migrated recipe");checked++;
        }
        report.addProperty("new_crafting_recipes_and_food_variants_checked",checked);
    }
    private static void loot(MinecraftServer server,JsonObject report)throws Exception{
        var definitions=LegacyBagDrops.definitionsForAudit();require(definitions.size()>1000,"Missing bag definitions");
        var rod=Class.forName("com.hbm.items.machine.ItemRBMKRod");int stateful=0;
        for(var key:List.of("2_0/lv2_pellet_rtg_radium","3_0/lv3_pellet_rtg_americium")){
            var stack=definitions.get(key);long expected=key.startsWith("2")?11520000000L:3384000000L;
            require(((Number)stack.getItem().getClass().getMethod("getLifespan",ItemStack.class).invoke(stack.getItem(),stack)).longValue()==expected,"RTG reward loses lifetime "+key);stateful++;
        }
        for(var fuel:List.of("heaus","mep","ra226be")){
            var stack=definitions.get("3_0/lv3_rbmk_fuel_"+fuel);
            require(((Number)rod.getMethod("getYield",ItemStack.class).invoke(null,stack)).doubleValue()==100000000D,"RBMK reward loses yield");
            require(((Number)rod.getMethod("getCoreHeat",ItemStack.class).invoke(null,stack)).doubleValue()==20D,"RBMK reward core heat differs");
            require(((Number)rod.getMethod("getHullHeat",ItemStack.class).invoke(null,stack)).doubleValue()==20D,"RBMK reward hull heat differs");stateful++;
        }
        var spark=definitions.get("5_0/lv5_battery_spark");
        require(spark.getOrCreateTag().getLong("charge")==100000000L,"Spark reward loses its inert legacy tag");stateful++;
        report.addProperty("spark_is_original_plain_item_with_legacy_charge_tag",true);
        var a=player(server,"LootLimitsA");var b=player(server,"LootLimitsB");
        String targetA=a.getUUID()+"_1_lv1_protection_charm",targetB=b.getUUID()+"_1_lv1_protection_charm";
        var initialCounts=LegacyBagDrops.limitsForAudit(server.overworld());
        var initial=new JsonObject();initial.addProperty("a",initialCounts.getOrDefault(targetA,0));initial.addProperty("b",initialCounts.getOrDefault(targetB,0));
        report.add("rare_loot_limits_before_sampling",initial);
        var checkpointPath=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore/loot-limit-checkpoint.json");
        if(java.nio.file.Files.exists(checkpointPath)){
            var prior=JsonParser.parseString(java.nio.file.Files.readString(checkpointPath)).getAsJsonObject();
            require(targetA.equals(prior.get("key_a").getAsString())&&targetB.equals(prior.get("key_b").getAsString()),"Loot persistence checkpoint belongs to different test players");
            require(initialCounts.getOrDefault(targetA,0)==prior.get("value_a").getAsInt()&&initialCounts.getOrDefault(targetB,0)==prior.get("value_b").getAsInt(),"Saved loot limits were not restored before opening any bag");
            report.addProperty("rare_loot_limits_restored_from_checkpoint",true);
        }
        int samples=0;
        for(int tier=0;tier<7;tier++)for(int fortune=0;fortune<4;fortune++)for(int i=0;i<8;i++){
            require(!LegacyBagDrops.open(server.overworld(),a,tier,fortune).isEmpty(),"Bag yields no rewards "+tier+"_"+fortune);samples++;
        }
        for(int i=0;i<20000;i++){
            LegacyBagDrops.open(server.overworld(),a,1,3);LegacyBagDrops.open(server.overworld(),b,1,3);samples+=2;
            var counts=LegacyBagDrops.limitsForAudit(server.overworld());
            if(counts.getOrDefault(targetA,0)==1&&counts.getOrDefault(targetB,0)==1)break;
        }
        for(int i=0;i<1000;i++)LegacyBagDrops.open(server.overworld(),a,1,3);
        var counts=LegacyBagDrops.limitsForAudit(server.overworld());
        require(counts.getOrDefault(targetA,0)==1&&counts.getOrDefault(targetB,0)==1,"Per-player unique reward limits fail");
        var bagItem=BuiltInRegistries.ITEM.get(new ResourceLocation("ntnhcore:lootbag_1"));a.getInventory().clearContent();
        a.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(bagItem,2));a.getAbilities().instabuild=false;
        bagItem.use(server.overworld(),a,InteractionHand.MAIN_HAND);
        require(a.getMainHandItem().getCount()==1,"Survival bag is not consumed exactly once");
        var checkpoint=new JsonObject();checkpoint.addProperty("player_a",a.getUUID().toString());checkpoint.addProperty("player_b",b.getUUID().toString());
        checkpoint.addProperty("key_a",targetA);checkpoint.addProperty("key_b",targetB);checkpoint.addProperty("value_a",counts.get(targetA));checkpoint.addProperty("value_b",counts.get(targetB));
        var dir=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore");
        java.nio.file.Files.writeString(dir.resolve("loot-limit-checkpoint.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checkpoint));
        server.overworld().getDataStorage().save();
        report.addProperty("bag_pools_exercised",28);report.addProperty("bag_openings_sampled",samples+1000);
        report.addProperty("hbm_reward_states_checked",stateful);report.addProperty("rare_loot_limit_is_per_player",true);
        report.addProperty("survival_bag_consumed_once",true);report.addProperty("reward_template_instances_checked",definitions.size());
    }
    private static void moon(MinecraftServer server,JsonObject report){
        var level=server.getAllLevels().iterator();net.minecraft.server.level.ServerLevel moon=null;
        while(level.hasNext()){var candidate=level.next();if(candidate.dimension().location().toString().equals("ntmspace:mun"))moon=candidate;}
        require(moon!=null,"Moon dimension unavailable");var samples=new JsonArray();int solid=0;
        for(int x=0;x<=32;x+=16)for(int z=0;z<=32;z+=16){
            moon.getChunk(x>>4,z>>4);var row=new JsonObject();row.addProperty("x",x);row.addProperty("z",z);
            row.addProperty("height",moon.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z));
            boolean air=moon.getBlockState(new BlockPos(x,127,z)).isAir();if(!air)solid++;row.addProperty("air_at_127",air);samples.add(row);
        }
        report.add("fresh_moon_chunk_samples",samples);report.addProperty("moon_high_solid_samples",solid);
        require(solid==0,"Unexpected high moon terrain: inspect chunk samples");
    }
}
