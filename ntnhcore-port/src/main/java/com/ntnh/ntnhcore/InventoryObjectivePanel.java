package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import funwayguy.betterquesting.bootstrap.forge.objects.registry.ItemRegistry;
import funwayguy.betterquesting.bootstrap.forge.objects.types.StackItem;
import funwayguy.betterquesting.client.gui.controls.PanelStackSlot;
import funwayguy.betterquesting.client.gui.panels.CanvasEmpty;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.gui.panels.menus.CanvasScrolling;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import funwayguy.betterquesting.client.misc.IGuiRect;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;

/** Collection objectives display translated item names, actual counters, charge and fluids. */
final class InventoryObjectivePanel extends CanvasEmpty {
    final JsonObject spec;final int task,quest;
    InventoryObjectivePanel(IGuiRect bounds,JsonObject spec,int task,int quest){super(bounds);this.spec=spec;this.task=task;this.quest=quest;}
    @Override public void initPanel(){
        super.initPanel();if(spec==null)return;
        var data=ProgressManager.get().getProgress(ProfileManager.get().getLocal().userID).getTaskData(quest,task);
        boolean done=data.getBoolean("isComplete");var counters=data.getIntArray("counters");
        addPanel(new PanelTextBox(new GuiRectangle(0,0,getTransform().getWidth(),20),spec.get("any").getAsBoolean()?QuestText.text("ntnhcore.ui.any"):QuestText.text("ntnhcore.ui.all")));
        var scroll=new CanvasScrolling(new GuiRectangle(0,22,getTransform().getWidth(),Math.max(32,getTransform().getHeight()-22)));addPanel(scroll);
        int i=0;
        for(var value:spec.getAsJsonArray("requirements")){
            var req=value.getAsJsonObject();ItemStack stack;
            if(req.has("fluid")||req.has("cell_fluid"))stack=HbmQuestRequirement.display(req);
            else if(req.has("item"))stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(req.get("item").getAsString())));
            else{
                var tag=TagKey.create(Registries.ITEM,new ResourceLocation(req.get("tag").getAsString()));
                stack=BuiltInRegistries.ITEM.getTag(tag).flatMap(set->set.stream().findFirst()).map(holder->new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
            }
            var count=req.get("count").getAsInt();int have=done?count:i<counters.length?counters[i]:0;
            scroll.addPanel(new PanelStackSlot(new GuiRectangle(0,i*48,32,32),-1,ItemRegistry.convertStack(stack),StackItem.class,true));
            String extra=req.has("charge")?QuestText.text("ntnhcore.ui.charge")+req.get("charge").getAsLong():req.has("fluid")?"\n"+req.get("amount").getAsInt()+QuestText.text("ntnhcore.ui.mb"):"";
            scroll.addPanel(new PanelTextBox(new GuiRectangle(38,i*48,Math.max(40,getTransform().getWidth()-40),46),
                "#{eeeeee}"+stack.getHoverName().getString()+extra+"\n"+(have>=count?"#{55ff55}":"#{ff7777}")+have+"/"+count));i++;
        }
    }
}
