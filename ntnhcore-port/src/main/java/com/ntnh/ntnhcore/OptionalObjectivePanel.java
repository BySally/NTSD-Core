package com.ntnh.ntnhcore;

import funwayguy.betterquesting.bootstrap.forge.objects.types.StackItem;
import funwayguy.betterquesting.client.gui.controls.PanelStackSlot;
import funwayguy.betterquesting.client.gui.panels.CanvasEmpty;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import funwayguy.betterquesting.client.misc.IGuiRect;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;

/** Uses actual acquisition data, never the always-open optional progression gate. */
final class OptionalObjectivePanel extends CanvasEmpty {
    final OptionalInventoryObjective objective;final int task,quest;
    OptionalObjectivePanel(IGuiRect bounds,OptionalInventoryObjective objective,int task,int quest){super(bounds);this.objective=objective;this.task=task;this.quest=quest;}
    @Override public void initPanel(){
        super.initPanel();
        var data=ProgressManager.get().getProgress(ProfileManager.get().getLocal().userID).getTaskData(quest,task);
        boolean acquired=data.getBoolean("optional_acquired");int[] counters=data.getIntArray("optional_counters");
        for(int i=0;i<objective.requiredItems.size();i++){
            var item=objective.displayItem(i);int amount=acquired?item.count:i<counters.length?counters[i]:0;
            addPanel(new PanelStackSlot(new GuiRectangle(0,i*44,28,28),-1,item,StackItem.class,true));
            var nativeStack=funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry.convertStack(item);
            addPanel(new PanelTextBox(new GuiRectangle(36,i*44,Math.max(40,getTransform().getWidth()-36),40),
                "#{eee9dc}"+nativeStack.getHoverName().getString()+"\n"+(acquired?QuestText.text("ntnhcore.ui.done"):QuestText.text("ntnhcore.ui.collected"))+amount+" / "+item.count).setFontSize(11));
        }
    }
}
