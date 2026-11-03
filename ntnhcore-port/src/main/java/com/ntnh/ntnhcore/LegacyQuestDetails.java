package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.IGuiCanvas;
import funwayguy.betterquesting.client.gui.WindowManager;
import funwayguy.betterquesting.client.gui.controls.PanelButton;
import funwayguy.betterquesting.client.gui.controls.scroll.PanelVScrollBar;
import funwayguy.betterquesting.client.gui.events.PEventBroadcaster;
import funwayguy.betterquesting.client.gui.events.types.PEventRefresh;
import funwayguy.betterquesting.client.gui.panels.CanvasEmpty;
import funwayguy.betterquesting.client.gui.panels.CanvasTextured;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.gui.panels.menus.CanvasScrolling;
import funwayguy.betterquesting.client.misc.GuiAlign;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import funwayguy.betterquesting.client.misc.GuiTransform;
import funwayguy.betterquesting.client.text.BQTranslate;
import funwayguy.betterquesting.client.themes.presets.PresetTexture;
import funwayguy.betterquesting.enums.ERefresh;
import funwayguy.betterquesting.network.handlers.NetQuestAction;
import funwayguy.betterquesting.questing.quests.Quest;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;

/** Responsive full-window pages, with native server-owned progress and claims. */
final class LegacyQuestDetails extends CanvasTextured {
    private final IGuiCanvas parent;private final int questID;
    private Quest quest;private CanvasScrolling objectives;
    private PanelButton claim;private long claimPendingUntil;
    LegacyQuestDetails(IGuiCanvas parent,int questID){super(new GuiTransform(GuiAlign.FULL_BOX),PresetTexture.PANEL_MAIN.getTexture());this.parent=parent;this.questID=questID;}
    @Override public void initPanel(){
        super.initPanel();quest=QuestDatabase.INSTANCE.getValue(questID);if(quest==null)return;
        int w=Math.max(1,getTransform().getWidth()-12);
        String description=BQTranslate.translate(quest.getDescKey());
        int h=Math.max(1,getTransform().getHeight()-12);
        if(Boolean.getBoolean("ntnhcore.validation"))org.slf4j.LoggerFactory.getLogger("ntnhcore").info("NTNH quest layout: root={}x{}, frame={}x{}, margin=6",getTransform().getWidth(),getTransform().getHeight(),w,h);
        var frame=new CanvasEmpty(new GuiRectangle(6,6,w,h));addPanel(frame);
        frame.addPanel(new BookPanel(new GuiRectangle(0,0,w,h),false));
        frame.addPanel(new PanelButton(new GuiRectangle(12,10,72,22),0,QuestText.text("ntnhcore.ui.back")).setClickAction(button->WindowManager.get().openWindowCanvas(parent)));
        frame.addPanel(new PanelTextBox(new GuiRectangle(94,14,w-112,24),"#{ffcc66}"+BQTranslate.translate(quest.getNameKey())).setAlignment(1));
        int left=w/2-30,right=w-w/2-38;
        frame.addPanel(new PanelTextBox(new GuiRectangle(18,48,left,20),QuestText.text("ntnhcore.ui.description")));
        frame.addPanel(new PanelTextBox(new GuiRectangle(w/2+18,48,right,20),QuestText.text("ntnhcore.ui.tasks")));
        frame.addPanel(new BookPanel(new GuiRectangle(w/2,48,1,h-64),false));
        CanvasScrolling text=new CanvasScrolling(new GuiRectangle(18,76,left,h-94));frame.addPanel(text);
        var desc=new PanelTextBox(new GuiRectangle(0,0,left-10,0),description,true);text.addPanel(desc);
        int y=desc.getTransform().getHeight()+18;
        if(quest.getRewards().size()>0){
            text.addPanel(new PanelTextBox(new GuiRectangle(0,y,left-10,20),QuestText.text("ntnhcore.ui.rewards")));y+=26;
            for(var reward:quest.getRewards().getEntries()){
                text.addPanel(reward.getValue().getPanel(new GuiRectangle(0,y,left-10,100),reward.getID(),questID));y+=110;
            }
            claim=new PanelButton(new GuiRectangle(0,y,left-10,22),2,QuestText.text("ntnhcore.ui.claim")).setClickAction(button->{
                if(!quest.canClaim(funwayguy.betterquesting.questing.player.profile.ProfileManager.get().getLocal().userID,questID))return;
                claimPendingUntil=System.currentTimeMillis()+3000;updateClaim();
                NetQuestAction.requestClaim(new int[]{questID});
            });
            text.addPanel(claim);updateClaim();
        }
        var leftBar=new PanelVScrollBar(new GuiRectangle(w/2-14,76,6,h-94));frame.addPanel(leftBar);text.setScrollDriverY(leftBar);
        objectives=new CanvasScrolling(new GuiRectangle(w/2+18,76,right,h-132));frame.addPanel(objectives);
        var rightBar=new PanelVScrollBar(new GuiRectangle(w-14,76,6,h-132));frame.addPanel(rightBar);objectives.setScrollDriverY(rightBar);
        frame.addPanel(new PanelButton(new GuiRectangle(w/2+18,h-44,right,24),1,QuestText.text("ntnhcore.ui.detect")).setClickAction(button->NetQuestAction.requestDetect(new int[]{questID})));
        rebuildObjectives();PEventBroadcaster.get().register(this::refresh,PEventRefresh.class);
        BookSounds.registerClicks();
    }
    private void updateClaim(){
        if(claim==null)return;
        var user=funwayguy.betterquesting.questing.player.profile.ProfileManager.get().getLocal().userID;
        boolean claimed=quest.hasClaimed(user,questID),pending=System.currentTimeMillis()<claimPendingUntil;
        claim.setActive(!pending&&quest.canClaim(user,questID));
        claim.setText(claimed?QuestText.text("ntnhcore.ui.claimed"):pending?QuestText.text("ntnhcore.ui.claiming"):QuestText.text("ntnhcore.ui.claim"));
    }
    @Override public void drawPanel(int mx,int my,float tick){updateClaim();super.drawPanel(mx,my,tick);}
    private void rebuildObjectives(){
        int previousY=objectives.getScrollY();objectives.resetCanvas();int y=0;int width=objectives.getTransform().getWidth()-4;
        for(boolean optional:new boolean[]{false,true}){
            boolean header=false;
            for(var entry:quest.getTasks().getEntries()){
                var task=entry.getValue();if((task instanceof OptionalInventoryObjective)!=optional)continue;
                if(!header){
                    if(optional)y+=12;
                    objectives.addPanel(new PanelTextBox(new GuiRectangle(0,y,width,20),optional?QuestText.text("ntnhcore.ui.optional"):QuestText.text("ntnhcore.ui.required")));y+=24;
                    boolean delivery=quest.getTasks().getEntries().stream().anyMatch(t->t.getValue() instanceof TaskRetrieval r&&r.consume&&!r.autoConsume);
                    boolean collection=quest.getTasks().getEntries().stream().anyMatch(t->t.getValue() instanceof TaskRetrieval||t.getValue() instanceof BQQuestTasks.Inventory);
                    var note=new PanelTextBox(new GuiRectangle(0,y,width,0),optional?QuestText.text("ntnhcore.ui.optional_note"):delivery?QuestText.text("ntnhcore.ui.delivery_note"):collection?QuestText.text("ntnhcore.ui.collection_note"):QuestText.text("ntnhcore.ui.conditions_note"),true).setFontSize(10);
                    objectives.addPanel(note);y+=note.getTransform().getHeight()+12;header=true;
                }
                int height=task instanceof OptionalInventoryObjective inventory?Math.max(62,inventory.requiredItems.size()*44+12):task instanceof TaskRetrieval retrieval?Math.max(76,24+retrieval.requiredItems.size()*44):118;
                objectives.addPanel(new BookPanel(new GuiRectangle(0,y,width,height),false));
                var bounds=new GuiRectangle(8,y+6,Math.max(40,width-16),height-12);
                objectives.addPanel(task instanceof TaskRetrieval retrieval?new RetrievalObjectivePanel(bounds,retrieval,entry.getID(),questID):task.getPanel(bounds,entry.getID(),questID));y+=height+12;
            }
        }
        objectives.refreshScrollBounds();var bounds=objectives.getScrollBounds();
        objectives.setScrollY(Math.max(bounds.getY(),Math.min(bounds.getY()+bounds.getHeight(),previousY)));
    }
    private void refresh(PEventRefresh event){if(event.shouldListen(ERefresh.QUEST,ERefresh.TASK,ERefresh.REWARD)&&objectives!=null){claimPendingUntil=0;updateClaim();rebuildObjectives();}}
}
