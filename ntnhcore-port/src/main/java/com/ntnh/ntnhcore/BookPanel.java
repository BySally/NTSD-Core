package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.IGuiPanel;
import funwayguy.betterquesting.client.gui.resources.lines.GuiLineSimple;
import funwayguy.betterquesting.client.math.Vec2f;
import funwayguy.betterquesting.client.misc.IGuiRect;

/** Resolution-independent borders and a quiet animated star/grid backdrop. */
final class BookPanel implements IGuiPanel {
    private final IGuiRect bounds;private final boolean ambient;private boolean enabled=true;
    private final GuiLineSimple line=new GuiLineSimple(1024,(short)0xffff);
    BookPanel(IGuiRect bounds,boolean ambient){this.bounds=bounds;this.ambient=ambient;}
    @Override public IGuiRect getTransform(){return bounds;}
    @Override public void initPanel(){}
    @Override public void setEnabled(boolean value){enabled=value;}
    @Override public boolean isEnabled(){return enabled;}
    private void stroke(float x,float y,float x2,float y2,int color,float z){line.drawLine(new Vec2f(x,y),new Vec2f(x2,y2),1,z,1,color);}
    @Override public void drawPanel(int mx,int my,float partialTick){
        float x=bounds.getX(),y=bounds.getY(),w=bounds.getWidth(),h=bounds.getHeight();
        if(!ambient){int color=0x506b8b8b;float z=bounds.getDepth();stroke(x,y,x+w,y,color,z);stroke(x,y+h,x+w,y+h,color,z);stroke(x,y,x,y+h,color,z);stroke(x+w,y,x+w,y+h,color,z);return;}
        float layer=bounds.getDepth();
        for(int dx=0;dx<w;dx+=48)stroke(x+dx,y,x+dx,y+h,0x18679999,layer);
        for(int dy=0;dy<h;dy+=48)stroke(x,y+dy,x+w,y+dy,0x18679999,layer);
        double time=System.nanoTime()/1_000_000_000.0;
        for(int i=0;i<40;i++){
            float px=x+(float)((i*137+29+time*(i%3+1)*.7)%Math.max(1,(int)w)),py=y+(float)((i*83+17+time*.35)%Math.max(1,(int)h));
            int alpha=25+(int)(18*(1+Math.sin(time*.65+i*1.7)));int color=(alpha<<24)|0x99cccc;
            stroke(px-1,py,px+1,py,color,layer);stroke(px,py-1,px,py+1,color,layer);
        }
        float scan=y+(float)((time*5)%Math.max(1,(int)h));
        for(int i=0;i<8;i++)if(scan+i*2<y+h)stroke(x,scan+i*2,x+w,scan+i*2,((12-i)<<24)|0x77bbbb,layer);
    }
}
