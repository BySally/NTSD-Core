package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.resources.textures.IGuiTexture;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import funwayguy.betterquesting.client.themes.presets.PresetTexture;

/** Original NTNH texture, with a quiet animated blueprint layer behind all controls. */
final class QuestBackdrop implements IGuiTexture {
    private final PresetTexture base;
    QuestBackdrop(){this(PresetTexture.PANEL_MAIN);}
    QuestBackdrop(PresetTexture base){this.base=base;}
    @Override public void drawTexture(int x,int y,int w,int h,float depth,float tick,funwayguy.betterquesting.client.gui.resources.colors.IGuiColor color){drawTexture(x,y,w,h,depth,tick);}
    @Override public void drawTexture(int x,int y,int w,int h,float depth,float tick){
        base.getTexture().drawTexture(x,y,w,h,depth,tick);
        new BookPanel(new GuiRectangle(x,y,w,h,Math.round(depth)+1),true).drawPanel(0,0,tick);
    }
}
