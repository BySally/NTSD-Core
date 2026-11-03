package com.ntnh.ntnhcore;

import com.google.gson.*;
import funwayguy.betterquesting.properties.CommonProps;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Compare actual loaded BQ rules and native unlock predicates against the original files. */
final class OriginalQuestRulesVerification {
    static void run(net.minecraft.server.MinecraftServer server,JsonObject report,JsonArray errors){
        if(!Boolean.getBoolean("ntnhcore.validation"))return;
        try(var input=OriginalQuestRulesVerification.class.getResourceAsStream("/ntnhcore/original-quest-rules.json")){
            var rules=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonArray();
            var user=java.util.UUID.randomUUID();ProfileManager.get().makeOrUpdate(user,"OriginalRulesCheck");
            var progress=ProgressManager.get().getProgress(user);int before=errors.size(),cases=0,edges=0,repeat=0;
            for(var value:rules){
                var rule=value.getAsJsonObject();int id=rule.get("quest").getAsInt();var quest=QuestDatabase.INSTANCE.getValue(id);
                int[] deps=rule.getAsJsonArray("prerequisites").asList().stream().mapToInt(JsonElement::getAsInt).toArray();edges+=deps.length;
                if(quest==null){errors.add("Original quest missing: "+id);continue;}
                if(!java.util.Arrays.equals(deps,quest.getRequirements()))errors.add("Original prerequisites differ: "+id);
                var info=quest.getInfo();
                if(!info.getProp(CommonProps.LOGIC_QUEST).name().equals(rule.get("quest_logic").getAsString())
                    ||!info.getProp(CommonProps.LOGIC_TASK).name().equals(rule.get("task_logic").getAsString())
                    ||!info.getProp(CommonProps.VISIBILITY).name().equals(rule.get("visibility").getAsString())
                    ||info.getProp(CommonProps.REPEAT_TIME)!=rule.get("repeat_time").getAsInt()
                    ||info.getProp(CommonProps.REPEAT_REL)!=rule.get("repeat_relative").getAsBoolean()
                    ||!info.getProp(CommonProps.SOUND_COMPLETE).equals(rule.get("snd_complete").getAsString()))errors.add("Original quest flags differ: "+id);
                if(info.getProp(CommonProps.REPEAT_TIME)>=0)repeat++;
                for(int dep:deps)progress.setQuestDone(dep,false);
                for(int done=0;done<=deps.length;done++){
                    if(done>0)progress.setQuestDone(deps[done-1],true);
                    boolean expected=deps.length==0||(rule.get("quest_logic").getAsString().equals("OR")?done>0:done==deps.length);
                    if(quest.isUnlocked(user)!=expected)errors.add("Original unlock predicate differs: "+id+" completed="+done);
                    cases++;
                }
                for(int dep:deps)progress.setQuestDone(dep,false);
            }
            var result=new JsonObject();result.addProperty("quests",rules.size());result.addProperty("prerequisite_edges",edges);
            result.addProperty("native_unlock_count_cases",cases);result.addProperty("repeatable_bag_exchanges",repeat);
            result.addProperty("matches_original",errors.size()==before);result.addProperty("original_cycles_preserved",true);
            report.add("original_quest_rules",result);
            var nuclear=QuestDatabase.INSTANCE.getEntries().stream().filter(e->e.getValue().getInfo().getProp(CommonProps.LOGIC_TASK)==funwayguy.betterquesting.enums.ELogic.OR&&e.getValue().getTasks().getEntries().stream().anyMatch(t->t.getValue() instanceof OptionalInventoryObjective)).findFirst().orElseThrow();
            var player=new net.minecraft.server.level.ServerPlayer(server,server.overworld(),new com.mojang.authlib.GameProfile(user,"OriginalRulesCheck"));
            for(var task:nuclear.getValue().getTasks().getEntries())if(task.getValue() instanceof OptionalInventoryObjective optional)optional.update(player,progress,nuclear.getID(),task.getID());
            if(nuclear.getValue().update(user,nuclear.getID()))errors.add("Optional OR objective incorrectly completed the nuclear quest");
            result.addProperty("optional_or_does_not_complete_required_quest",!progress.isQuestDone(nuclear.getID()).first());
        }catch(Exception error){errors.add("Original rules verification: "+error);}
    }
}
