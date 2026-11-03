package com.ntnh.ntnhcore;

import com.google.gson.*;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;
import funwayguy.betterquesting.questing.player.cache.QuestCacheManager;
import funwayguy.betterquesting.questing.GlobalSettings;
import funwayguy.betterquesting.properties.CommonProps;

final class BQProgressAudit {
    static int write(net.minecraft.server.MinecraftServer server){
        JsonObject root=new JsonObject();
        root.addProperty("edit_mode",GlobalSettings.get().getProp(CommonProps.EDIT_MODE));
        JsonArray players=new JsonArray();
        for(var player:server.getPlayerList().getPlayers()){
            JsonObject row=new JsonObject();row.addProperty("name",player.getGameProfile().getName());
            row.addProperty("quest_book_given",player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean(QuestBookGrant.KEY));
            var progress=ProgressManager.get().getProgress(player.getUUID());
            row.addProperty("active_quests",QuestCacheManager.get().getCache(player.getUUID()).getActiveQuests().length);
            JsonObject quests=new JsonObject();
            for(var entry:QuestDatabase.INSTANCE.getEntries()){
                JsonObject q=new JsonObject();q.addProperty("title",entry.getValue().getNameKey());
                q.addProperty("state",progress.getQuestState(entry.getID()).toString());
                q.addProperty("unlocked",entry.getValue().isUnlocked(player.getUUID()));
                JsonObject tasks=new JsonObject();
                for(var task:entry.getValue().getTasks().getEntries())tasks.addProperty(Integer.toString(task.getID()),progress.getTaskData(entry.getID(),task.getID()).getBoolean("isComplete"));
                q.add("tasks",tasks);quests.add(Integer.toString(entry.getID()),q);
            }
            row.add("quests",quests);JsonObject inventory=new JsonObject();
            for(var stack:player.getInventory().items)if(!stack.isEmpty()){
                String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                inventory.addProperty(id,stack.getCount()+(inventory.has(id)?inventory.get(id).getAsInt():0));
            }
            row.add("inventory",inventory);players.add(row);
        }
        root.add("players",players);
        try{
            var dir=net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("quest-progress-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(root),java.nio.charset.StandardCharsets.UTF_8);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        return 1;
    }
}
