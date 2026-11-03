package com.ntnh.ntnhcore;

import com.google.gson.*;
import funwayguy.betterquesting.client.gui.IGuiPanel;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.misc.IGuiRect;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.tasks.Task;
import funwayguy.betterquesting.questing.tasks.TaskRegistry;
import funwayguy.betterquesting.registry.ResourcePath;
import funwayguy.xbt.std.XBTTagCompound;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Native BQ 4 tasks. Inventory consumption is atomic and only runs on the server. */
public final class BQQuestTasks {
    private static JsonObject definitions;
    private static int[] optionalQuests=new int[0];
    private record Scan(long fingerprint,int tick){}
    private static final java.util.Map<net.minecraft.server.level.ServerPlayer,Scan> SCANS=new java.util.WeakHashMap<>();
    public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof net.minecraft.server.level.ServerPlayer player)
            || player.tickCount%20!=0 || funwayguy.betterquesting.questing.GlobalSettings.get().getProp(funwayguy.betterquesting.properties.CommonProps.EDIT_MODE))return;
        var active=funwayguy.betterquesting.questing.player.cache.QuestCacheManager.get().getCache(player.getUUID()).getActiveQuests();
        long fingerprint=java.util.Arrays.hashCode(active);
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            var held=player.getInventory().getItem(slot);
            fingerprint=31*fingerprint+net.minecraft.world.item.Item.getId(held.getItem());
            fingerprint=31*fingerprint+held.getCount();fingerprint=31*fingerprint+(held.hasTag()?held.getTag().hashCode():0);
        }
        var last=SCANS.get(player);
        if(last!=null&&last.fingerprint()==fingerprint&&player.tickCount-last.tick()<100)return;
        SCANS.put(player,new Scan(fingerprint,player.tickCount));
        var participant=new ParticipantInfo(player.getUUID());
        for(int id:active){
            var quest=funwayguy.betterquesting.questing.quests.QuestDatabase.INSTANCE.getValue(id);
            if(quest==null)continue;
            for(var task:quest.getTasks().getEntries()){
                if(task.getValue() instanceof Inventory inventory)inventory.detect(new ParticipantInfo(player.getUUID()),task.getID(),id);
                else if(task.getValue() instanceof funwayguy.bq_standard.questing.tasks.TaskRetrieval retrieval
                    &&(!retrieval.consume||retrieval.autoConsume)&&!participant.PLAYER.progress.getTaskData(id,task.getID()).getBoolean("isComplete"))
                    retrieval.submit(participant,task.getID(),id);
            }
        }
        // Optional acquisitions remain visible after the required objectives are complete.
        for(int id:optionalQuests){
            var quest=funwayguy.betterquesting.questing.quests.QuestDatabase.INSTANCE.getValue(id);
            if(quest!=null&&quest.isUnlocked(player.getUUID()))for(var task:quest.getTasks().getEntries())
                if(task.getValue() instanceof OptionalInventoryObjective optional)optional.detect(new ParticipantInfo(player.getUUID()),task.getID(),id);
        }
    }
    public static void install() {
        LegacyIconRegistry.install();
        try(var stream=BQQuestTasks.class.getResourceAsStream("/ntnhcore/tag-tasks.json")) {
            if(stream==null)throw new IllegalStateException("Missing NTNH tag task definitions");
            definitions=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException error){throw new IllegalStateException(error);}
        try(var stream=BQQuestTasks.class.getResourceAsStream("/ntnhcore/optional-quests.json")){
            if(stream!=null)optionalQuests=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray().asList().stream().mapToInt(JsonElement::getAsInt).toArray();
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
        TaskRegistry.get().register(new ResourcePath("ntnhcore:pending"),Pending::new);
        TaskRegistry.get().register(new ResourcePath("ntnhcore:tag_inventory"),Inventory::new);
        TaskRegistry.get().register(new ResourcePath("ntnhcore:optional_flint_shovel"),OptionalFlintObjective::new);
        TaskRegistry.get().register(new ResourcePath("ntnhcore:optional_inventory"),OptionalInventoryObjective::new);
    }
    public static class Pending extends Task {
        protected String title="ntnhcore.ui.pending_description";
        protected String legacyID="";
        @Override public ResourcePath getID(){return new ResourcePath("ntnhcore:pending");}
        @Override public String getNameKey(){return "ntnhcore.ui.pending";}
        @Override public void detect(ParticipantInfo participant,int taskID,int questID){}
        @Override public IGuiPanel getPanel(IGuiRect bounds,int taskID,int questID){return new PanelTextBox(bounds,funwayguy.betterquesting.client.text.BQTranslate.translate(title));}
        @Override public XBTTagCompound writeToXBT(XBTTagCompound data){
            data.setString("title",title);data.setString("ftb_id",legacyID);return data;
        }
        @Override public void readFromXBT(XBTTagCompound data){title=data.getString("title");legacyID=data.getString("ftb_id");}
    }
    public static final class Inventory extends Pending {
        @Override public ResourcePath getID(){return new ResourcePath("ntnhcore:tag_inventory");}
        @Override public String getNameKey(){return "ntnhcore.ui.collect";}
        @Override public IGuiPanel getPanel(IGuiRect bounds,int task,int quest){return new InventoryObjectivePanel(bounds,definitions==null?null:definitions.getAsJsonObject(legacyID),task,quest);}
        @Override public void detect(ParticipantInfo participant,int taskID,int questID){
            if(participant.PLAYER.progress.getTaskData(questID,taskID).getBoolean("isComplete"))return;
            var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if(server==null || definitions==null || !definitions.has(legacyID))return;
            var player=server.getPlayerList().getPlayer(participant.PLAYER.playerID);
            if(player==null)return;
            var spec=definitions.getAsJsonObject(legacyID);var inventory=player.getInventory();
            int[] available=new int[inventory.getContainerSize()];
            for(int i=0;i<available.length;i++)available[i]=inventory.getItem(i).getCount();
            boolean any=spec.get("any").getAsBoolean(),success=!any;
            int[] counters=new int[spec.getAsJsonArray("requirements").size()];int requirementIndex=0;
            for(var requirement:spec.getAsJsonArray("requirements")) {
                var req=requirement.getAsJsonObject();
                TagKey<Item> tag=req.has("tag")?TagKey.create(Registries.ITEM,new ResourceLocation(req.get("tag").getAsString())):null;
                ResourceLocation item=req.has("item")?new ResourceLocation(req.get("item").getAsString()):null;
                int[] attempt=available.clone();int remaining=req.get("count").getAsInt();
                for(int i=0;i<attempt.length&&remaining>0;i++){
                    var stack=inventory.getItem(i);if(stack.isEmpty())continue;
                    boolean matches=tag!=null?stack.is(tag):net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
                    if(matches&&HbmQuestRequirement.matches(req,stack)){int take=Math.min(remaining,attempt[i]);attempt[i]-=take;remaining-=take;}
                }
                counters[requirementIndex++]=req.get("count").getAsInt()-remaining;
                if(remaining==0){available=attempt;if(any){success=true;break;}}
                else if(!any){success=false;break;}
            }
            var data=participant.PLAYER.progress.getTaskData(questID,taskID);
            if(!java.util.Arrays.equals(data.getIntArray("counters"),counters)){data.setIntArray("counters",counters);participant.markDirty(questID);}
            if(!success)return;
            if(spec.get("consume").getAsBoolean()){
                for(int i=0;i<available.length;i++)inventory.getItem(i).shrink(inventory.getItem(i).getCount()-available[i]);
                inventory.setChanged();player.containerMenu.broadcastChanges();
            }
            participant.PLAYER.progress.setTaskDone(questID,taskID,true);
            participant.markDirty(questID);
        }
    }
}
