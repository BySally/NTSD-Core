package com.ntnh.ntnhcore;

import funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry;
import funwayguy.betterquesting.client.gui.IGuiPanel;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.misc.IGuiRect;
import funwayguy.betterquesting.objects.StackLibrary;
import funwayguy.betterquesting.objects.StackedObj;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.tasks.Task;
import funwayguy.betterquesting.registry.ResourcePath;
import funwayguy.xbt.std.XBTTagCompound;
import funwayguy.xbt.std.XBTTagList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Original optional retrievals never consume items and never gate quest progression. */
final class OptionalInventoryObjective extends Task {
    final List<StackedObj> requiredItems=new ArrayList<>();
    String unavailable="";
    JsonObject itemConstraints=new JsonObject();
    @Override public ResourcePath getID(){return new ResourcePath("ntnhcore:optional_inventory");}
    @Override public String getNameKey(){return "ntnhcore.ui.optional_name";}
    @Override public void detect(ParticipantInfo participant,int task,int quest){
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        var player=server.getPlayerList().getPlayer(participant.PLAYER.playerID);if(player==null)return;
        if(update(player,participant.PLAYER.progress,quest,task))participant.markDirty(quest);
    }
    boolean update(net.minecraft.server.level.ServerPlayer player,funwayguy.betterquesting.questing.player.progress.PlayerProgress progress,int quest,int task){
        var data=progress.getTaskData(quest,task);
        var definition=funwayguy.betterquesting.questing.quests.QuestDatabase.INSTANCE.getValue(quest);
        boolean gate=definition==null||definition.getInfo().getProp(funwayguy.betterquesting.properties.CommonProps.LOGIC_TASK)!=funwayguy.betterquesting.enums.ELogic.OR;
        boolean changed=data.getBoolean("isComplete")!=gate;
        // Native retrieval progress from a previous pack version is evidence of acquisition.
        int[] previous=data.getIntArray("counters");
        if(!data.getBoolean("optional_acquired")&&unavailable.isEmpty()&&itemConstraints.size()==0&&!requiredItems.isEmpty()&&previous.length==requiredItems.size()){
            boolean alreadyAcquired=true;
            for(int i=0;i<previous.length;i++)alreadyAcquired&=previous[i]>=requiredItems.get(i).count;
            if(alreadyAcquired){data.setBoolean("optional_acquired",true);changed=true;}
        }
        // The completion flag is only a progression gate. Acquisition is recorded independently.
        if(changed)progress.setTaskDone(quest,task,gate);
        if(unavailable.isEmpty()&&!requiredItems.isEmpty()&&!data.getBoolean("optional_acquired")){
            int[] counters=new int[requiredItems.size()];boolean acquired=true;
            for(int i=0;i<requiredItems.size();i++){
                var wanted=ItemRegistry.convertStack(requiredItems.get(i));int count=0;
                for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
                    var held=player.getInventory().getItem(slot);
                    var constraint=itemConstraints.getAsJsonObject(Integer.toString(i));
                    if(!wanted.isEmpty()&&held.is(wanted.getItem())&&(constraint==null||HbmQuestRequirement.matches(constraint,held)))count+=held.getCount();
                }
                counters[i]=Math.min(count,requiredItems.get(i).count);
                acquired&=counters[i]>=requiredItems.get(i).count;
            }
            if(!Arrays.equals(data.getIntArray("optional_counters"),counters)){
                data.setIntArray("optional_counters",counters);changed=true;
            }
            if(acquired){data.setBoolean("optional_acquired",true);changed=true;}
        }
        return changed;
    }
    @Override public void submit(ParticipantInfo participant,int task,int quest){detect(participant,task,quest);}
    StackedObj displayItem(int index){
        var constraint=itemConstraints.getAsJsonObject(Integer.toString(index));
        if(constraint==null)return requiredItems.get(index);
        var stack=HbmQuestRequirement.display(constraint);stack.setCount(requiredItems.get(index).count);
        return ItemRegistry.convertStack(stack);
    }
    @Override public IGuiPanel getPanel(IGuiRect bounds,int task,int quest){
        if(!unavailable.isEmpty())return new PanelTextBox(bounds,QuestText.text("ntnhcore.ui.optional_pending")+unavailable+QuestText.text("ntnhcore.ui.optional_pending_note"));
        return new OptionalObjectivePanel(bounds,this,task,quest);
    }
    @Override public XBTTagCompound writeToXBT(XBTTagCompound data){
        var list=new XBTTagList();
        for(var item:requiredItems)list.appendTag(item.writeToXBT(new XBTTagCompound()));
        data.setTag("requiredItems",list);data.setString("unavailable",unavailable);data.setString("item_constraints",itemConstraints.toString());return data;
    }
    @Override public void readFromXBT(XBTTagCompound data){
        requiredItems.clear();unavailable=data.getString("unavailable");
        String constraints=data.getString("item_constraints");itemConstraints=constraints.isBlank()?new JsonObject():JsonParser.parseString(constraints).getAsJsonObject();
        for(var tag:data.getList("requiredItems").getAsList(XBTTagCompound.class))requiredItems.add(StackLibrary.get().loadStack(tag));
    }
}
