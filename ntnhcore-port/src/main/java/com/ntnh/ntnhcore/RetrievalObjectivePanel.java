package com.ntnh.ntnhcore;

import funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry;
import funwayguy.betterquesting.bootstrap.forge.objects.types.StackItem;
import funwayguy.betterquesting.client.gui.controls.PanelStackSlot;
import funwayguy.betterquesting.client.gui.panels.CanvasEmpty;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import funwayguy.betterquesting.client.misc.IGuiRect;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;

/** Display native persisted counters without altering matching or consumption. */
final class RetrievalObjectivePanel extends CanvasEmpty {
    private final TaskRetrieval task;private final int taskID,questID;
    RetrievalObjectivePanel(IGuiRect bounds,TaskRetrieval task,int taskID,int questID){super(bounds);this.task=task;this.taskID=taskID;this.questID=questID;}
    @Override public void initPanel(){
        super.initPanel();var data=ProgressManager.get().getProgress(ProfileManager.get().getLocal().userID).getTaskData(questID,taskID);
        int[] counters=data.getIntArray("counters");boolean done=data.getBoolean("isComplete");int width=getTransform().getWidth();
        addPanel(new PanelTextBox(new GuiRectangle(0,0,width,16),"#{b5c8cb}"+(task.consume?QuestText.text("ntnhcore.ui.consume"):QuestText.text("ntnhcore.ui.keep"))).setFontSize(10));
        for(int i=0;i<task.requiredItems.size();i++){
            var required=task.requiredItems.get(i);var stack=ItemRegistry.convertStack(required);int count=required.count;int have=done?count:i<counters.length?counters[i]:0;int y=20+i*44;
            addPanel(new PanelStackSlot(new GuiRectangle(0,y,28,28),-1,required,StackItem.class,true));
            addPanel(new PanelTextBox(new GuiRectangle(36,y,Math.max(30,width-36),40),"#{eee9dc}"+stack.getHoverName().getString()+"\n"+(have>=count?QuestText.text("ntnhcore.ui.done"):QuestText.text("ntnhcore.ui.collected"))+have+" / "+count).setFontSize(11));
        }
    }
}
