package com.ntnh.ntnhcore;

import com.google.gson.*;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.betterquesting.questing.chapters.ChapterDatabase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Verify the loaded pack, after Forge, quest and datapack reload listeners have run. */
final class PackVerification {
    static int run(net.minecraft.server.MinecraftServer server) {
        var report=new JsonObject();var errors=new JsonArray();
        try(var input=PackVerification.class.getResourceAsStream("/ntnhcore/verification.json")) {
            var expected=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            var file=QuestDatabase.INSTANCE;
            int quests=file.size();
            int tasks=file.getEntries().stream().mapToInt(q->q.getValue().getTasks().size()).sum();
            report.addProperty("quests",quests);report.addProperty("tasks",tasks);
            report.addProperty("quest_engine","Better Questing 4");
            report.addProperty("chapters",ChapterDatabase.INSTANCE.size());
            if(ChapterDatabase.INSTANCE.size()!=13)errors.add("Loaded chapter count differs");
            var sorted=ChapterDatabase.INSTANCE.getSortedEntries();var order=BQChapterOrder.read();
            var chapterIDs=new JsonArray();sorted.forEach(entry->chapterIDs.add(entry.getID()));report.add("chapter_order",chapterIDs);
            for(int i=0;i<Math.min(order.length,sorted.size());i++)if(sorted.get(i).getID()!=order[i])errors.add("Chapter order differs at "+i);
            if(quests!=expected.get("quests").getAsInt())errors.add("Loaded quest count differs");
            if(tasks!=expected.get("tasks").getAsInt())errors.add("Loaded task count differs");
            long placeholders=file.getEntries().stream().flatMap(q->q.getValue().getTasks().getEntries().stream())
                .filter(t->t.getValue() instanceof funwayguy.betterquesting.questing.tasks.TaskPlaceholder).count();
            report.addProperty("unknown_task_types",placeholders);
            if(placeholders!=0)errors.add("Unknown quest task types loaded");
            long pending=file.getEntries().stream().flatMap(q->q.getValue().getTasks().getEntries().stream())
                .filter(t->t.getValue().getClass()==BQQuestTasks.Pending.class).count();
            report.addProperty("pending_legacy_tasks",pending);
            if(pending!=expected.get("pending_tasks").getAsInt())errors.add("Pending task count differs from this revision");
            int rewardGroups=0,rewardStacks=0,retrievalStacks=0,enchantedRewards=0;
            for(var entry:file.getEntries()){
                for(var task:entry.getValue().getTasks().getEntries())if(task.getValue() instanceof funwayguy.bq_standard.questing.tasks.TaskRetrieval retrieval){
                    if(retrieval.requiredItems.isEmpty())errors.add("Empty retrieval task "+entry.getID());
                    for(var stack:retrieval.requiredItems){retrievalStacks++;if(stack.isEmpty())errors.add("Invalid required item "+entry.getID());}
                }
                for(var reward:entry.getValue().getRewards().getEntries())if(reward.getValue() instanceof funwayguy.bq_standard.questing.rewards.RewardItem items){
                    rewardGroups++;
                    if(items.items.isEmpty())errors.add("Empty item reward "+entry.getID());
                    for(var stack:items.items){
                        rewardStacks++;if(stack.isEmpty())errors.add("Invalid reward item "+entry.getID());
                        var nativeStack=funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry.convertStack(stack);
                        if(nativeStack.isEmpty())errors.add("Reward cannot be converted "+entry.getID());
                        if(nativeStack.isEnchanted())enchantedRewards++;
                    }
                }
            }
            report.addProperty("item_reward_groups",rewardGroups);report.addProperty("item_reward_stacks",rewardStacks);
            report.addProperty("retrieval_stacks",retrievalStacks);report.addProperty("enchanted_reward_stacks",enchantedRewards);
            if(rewardGroups!=expected.get("item_reward_groups").getAsInt() || rewardStacks!=expected.get("item_reward_stacks").getAsInt())errors.add("Reward item count differs");
            if(enchantedRewards!=expected.get("enchanted_reward_stacks").getAsInt())errors.add("Reward enchantments lost");
            OriginalQuestRulesVerification.run(server,report,errors);
            for(var id:expected.getAsJsonArray("bq_ids"))if(file.getValue(id.getAsInt())==null)errors.add("Missing BQ quest "+id.getAsInt());
            long bound=file.getEntries().stream().flatMap(q->q.getValue().getTasks().getEntries().stream())
                .filter(t->t.getValue() instanceof BQQuestTasks.Inventory).count();
            report.addProperty("bound_tag_tasks",bound);
            if(bound!=expected.get("tag_tasks").getAsInt())errors.add("Ore dictionary task callbacks not bound");
            int recipes=0;
            for(var id:expected.getAsJsonArray("recipes")){
                if(server.getRecipeManager().byKey(new ResourceLocation(id.getAsString())).isEmpty())errors.add("Missing recipe "+id.getAsString());
                else recipes++;
            }
            report.addProperty("migrated_recipes",recipes);
            for(var id:expected.getAsJsonArray("removed"))if(server.getRecipeManager().byKey(new ResourceLocation(id.getAsString())).isPresent())errors.add("Recipe removal not applied "+id.getAsString());
            var level=server.overworld();
            var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(level.getSharedSpawnPos())).create(LootContextParamSets.CHEST);
            int loot=0;
            for(int tier=0;tier<7;tier++)for(int fortune=0;fortune<4;fortune++) {
                var id=new ResourceLocation("ntnhcore","bags/"+tier+"_"+fortune);
                var table=server.getLootData().getLootTable(id);
                if(table.getRandomItems(params).isEmpty())errors.add("Empty or missing loot table "+id);else loot++;
            }
            report.addProperty("loot_tables_sampled",loot);
            var config=Class.forName("com.hbm.config.CustomMachineConfigJSON");
            var machines=(java.util.List<?>)config.getField("niceList").get(null);
            report.addProperty("custom_machines",machines.size());
            if(machines.size()!=2)errors.add("Expected both NTNH custom machines");
            var customRecipes=Class.forName("com.hbm.inventory.recipes.CustomMachineRecipes");
            var spec=JsonParser.parseString("[\"fluid_container\",\"hbm:fluid_tank\",1,\"AMAT\",1000]").getAsJsonArray();
            Object ingredient=customRecipes.getMethod("readAStack",JsonArray.class).invoke(null,spec);
            var astack=Class.forName("com.hbm.inventory.RecipesCommon$AStack");
            var representative=(net.minecraft.world.item.ItemStack)astack.getMethod("getDisplayStack").invoke(ingredient);
            var matches=astack.getMethod("matchesRecipe",net.minecraft.world.item.ItemStack.class,boolean.class);
            boolean full=(boolean)matches.invoke(ingredient,representative,true);
            boolean empty=(boolean)matches.invoke(ingredient,new net.minecraft.world.item.ItemStack(representative.getItem()),true);
            report.addProperty("antimatter_ingredient_full_accepted",full);
            report.addProperty("antimatter_ingredient_empty_rejected",!empty);
            if(!full || empty)errors.add("Antimatter container ingredient is unsafe");
            BookLifecycleVerification.run(server,report,errors);
            LegacyFlintVerification.run(server,report,errors);
        } catch(Exception error) {errors.add(error.toString());}
        if(Boolean.getBoolean("ntnhcore.validation")){
            ContentVerification.run(server,report,errors);
            InventoryProgressVerification.run(server,report,errors);
        }
        report.add("errors",errors);report.addProperty("passed",errors.isEmpty());
        try {
            var dir=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore");Files.createDirectories(dir);
            Files.writeString(dir.resolve("verification.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);
        } catch(Exception error){throw new IllegalStateException(error);}
        org.slf4j.LoggerFactory.getLogger("ntnhcore").info("NTNH verification: {}",report);
        return errors.isEmpty()?1:0;
    }
    private static void checkDependencies(int id,java.util.Set<Integer> visiting,
            java.util.Set<Integer> visited,JsonArray errors) {
        if(visited.contains(id))return;
        if(!visiting.add(id)){errors.add("Cyclic quest dependency at "+id);return;}
        var quest=QuestDatabase.INSTANCE.getValue(id);
        if(quest==null){errors.add("Missing dependency "+id);return;}
        for(int dependency:quest.getRequirements())checkDependencies(dependency,visiting,visited,errors);
        visiting.remove(id);visited.add(id);
    }
}
