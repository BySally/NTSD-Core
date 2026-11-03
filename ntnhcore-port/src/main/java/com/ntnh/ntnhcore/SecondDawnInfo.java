package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.*;
import funwayguy.betterquesting.client.gui.controls.PanelButton;
import funwayguy.betterquesting.client.gui.controls.scroll.PanelVScrollBar;
import funwayguy.betterquesting.client.gui.panels.CanvasTextured;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.gui.panels.menus.CanvasScrolling;
import funwayguy.betterquesting.client.gui.resources.colors.GuiColorStatic;
import funwayguy.betterquesting.client.gui.resources.textures.GuiTextureQuad;
import funwayguy.betterquesting.client.misc.*;

/** Extra information screens are not quests and never create or replace quest IDs. */
final class SecondDawnInfo extends CanvasTextured {
    private final IGuiCanvas parent;private final String topic;
    SecondDawnInfo(IGuiCanvas parent,String topic){super(new GuiTransform(GuiAlign.FULL_BOX),new GuiTextureQuad(new GuiColorStatic(0xff121820)));this.parent=parent;this.topic=topic;}
    @Override public void initPanel(){
        super.initPanel();int w=getTransform().getWidth(),h=getTransform().getHeight();
        int margin=Math.max(10,Math.min(28,w/24));
        addPanel(new PanelButton(new GuiRectangle(margin,12,76,22),0,QuestText.text("ntnhcore.ui.back")).setClickAction(b->WindowManager.get().openWindowCanvas(parent)));
        addPanel(new PanelTextBox(new GuiRectangle(margin+88,15,w-margin*2-88,28),"#{ffce86}"+QuestText.text("ntnhcore.ntsd."+topic+".title")).setAlignment(1));
        int contentW=w-margin*2-14;
        var text=new CanvasScrolling(new GuiRectangle(margin,52,contentW,Math.max(35,h-100)));addPanel(text);
        text.addPanel(new PanelTextBox(new GuiRectangle(0,0,contentW-12,0),QuestText.text("ntnhcore.ntsd."+topic+".body"),true));
        var bar=new PanelVScrollBar(new GuiRectangle(w-margin-7,52,6,Math.max(35,h-100)));addPanel(bar);text.setScrollDriverY(bar);
        int bw=Math.min(150,(w-margin*2-8)/2);
        for(int i=0;i<2;i++){
            String kind=i==0?"github_issues":"discord",url=SecondDawnLinks.get(kind);
            var button=new PanelButton(new GuiRectangle(margin+i*(bw+8),h-36,bw,23),i+1,i==0?"GitHub Issues":"Discord");
            button.setActive(!url.isBlank());button.setClickAction(b->net.minecraft.Util.getPlatform().openUri(url));addPanel(button);
        }
        BookSounds.registerClicks();
    }
}
