package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.WindowManager;
import funwayguy.betterquesting.client.gui.controls.PanelButton;
import funwayguy.betterquesting.client.gui.panels.CanvasTextured;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.misc.*;
import funwayguy.betterquesting.client.layouts.args.*;
import funwayguy.betterquesting.client.layouts.presets.PresetGui;
import funwayguy.betterquesting.questing.party.PartyManager;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.GlobalSettings;
import funwayguy.betterquesting.properties.CommonProps;

/** New presentation only: native BQ chapters, parties and progress remain authoritative. */
final class SecondDawnHome extends CanvasTextured {
    SecondDawnHome(){super(new GuiTransform(GuiAlign.FULL_BOX),new SecondDawnBackground());}
    @Override public void initPanel(){
        super.initPanel();int w=getTransform().getWidth(),h=getTransform().getHeight();
        int margin=Math.max(8,Math.min(24,w/30)),gap=5,bh=Math.max(18,Math.min(25,h/12));
        addPanel(new PanelTextBox(new GuiRectangle(margin,Math.max(14,h/14),w-margin*2,40),"#{ffce86}Nuclear Tech: Second Dawn").setAlignment(1).setFontSize(w<480?16:22));
        addPanel(new PanelTextBox(new GuiRectangle(margin,Math.max(56,h/14+44),w-margin*2,30),QuestText.text("ntnhcore.ntsd.tagline")).setAlignment(1));
        int bottom=h-margin-bh,secondary=bottom-gap-bh;
        String[] info={"intro","credits","issues"};
        for(int i=0;i<3;i++){
            final String topic=info[i];int x=margin+(w-margin*2)*i/3,ex=margin+(w-margin*2)*(i+1)/3-gap;
            addPanel(new PanelButton(new GuiRectangle(x,secondary,ex-x,bh),10+i,QuestText.text("ntnhcore.ntsd."+topic+".title"))
                .setClickAction(b->WindowManager.get().openWindowCanvas(new SecondDawnInfo(this,topic))));
        }
        String[] labels={"betterquesting.home.exit","betterquesting.home.quests","betterquesting.home.party","betterquesting.home.theme"};
        for(int i=0;i<4;i++){
            int index=i,x=margin+(w-margin*2)*i/4,ex=margin+(w-margin*2)*(i+1)/4-gap;
            var button=new PanelButton(new GuiRectangle(x,bottom,ex-x,bh),i,funwayguy.betterquesting.client.text.BQTranslate.translate(labels[i]));
            button.setClickAction(b->{
                switch(index){
                    case 0->WindowManager.get().closeWindow(true);
                    case 1->PresetGui.CHAPTERS.openWindow(new GArgBasic(this));
                    case 2->{var party=PartyManager.INSTANCE.getParty(ProfileManager.get().getLocal().userID);
                        PresetGui.PARTY.openWindow(new GArgValue<Integer>(this,party==null?-1:party.getID()));}
                    case 3->WindowManager.get().openWindowCanvas(new funwayguy.betterquesting.client.gui.screens.GuiThemes());
                }
            });
            if(i==2)button.setActive(GlobalSettings.get().getProp(CommonProps.PARTY_ENABLE));
            addPanel(button);
        }
        addPanel(new PanelTextBox(new GuiRectangle(margin,secondary-27,w-margin*2,20),QuestText.text("ntnhcore.ntsd.origin_short")).setAlignment(1).setFontSize(10));
        BookSounds.registerClicks();
    }
}
