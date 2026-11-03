package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.resources.textures.*;
import funwayguy.betterquesting.client.gui.resources.colors.*;
import funwayguy.betterquesting.client.misc.GuiRectangle;
import funwayguy.betterquesting.registry.ResourcePath;

/** Uses the supplied artwork unchanged. Centre-crop to cover, never stretch. */
final class SecondDawnBackground implements IGuiTexture {
    static final int IMAGE_WIDTH=1672, IMAGE_HEIGHT=941;
    static final ResourcePath IMAGE=new ResourcePath("ntnhcore:textures/gui/ntsd/main_menu.png");
    static int[] crop(int w,int h){
        if(w<=0||h<=0)return new int[]{0,0,IMAGE_WIDTH,IMAGE_HEIGHT};
        double ratio=(double)w/h;
        int cw=IMAGE_WIDTH,ch=IMAGE_HEIGHT;
        if(ratio>(double)IMAGE_WIDTH/IMAGE_HEIGHT)ch=Math.max(1,(int)Math.round(IMAGE_WIDTH/ratio));
        else cw=Math.max(1,(int)Math.round(IMAGE_HEIGHT*ratio));
        return new int[]{(IMAGE_WIDTH-cw)/2,(IMAGE_HEIGHT-ch)/2,cw,ch};
    }
    @Override public void drawTexture(int x,int y,int w,int h,float depth,float tick,IGuiColor ignored){drawTexture(x,y,w,h,depth,tick);}
    @Override public void drawTexture(int x,int y,int w,int h,float depth,float tick){
        if(w<=0||h<=0)return;
        int[] c=crop(w,h);
        new GuiTextureSimple(IMAGE,new GuiRectangle(c[0],c[1],c[2],c[3]),IMAGE_WIDTH,IMAGE_HEIGHT)
            .drawTexture(x,y,w,h,depth,tick);
        // UI-only shading: the original PNG, its resolution and alpha remain untouched.
        funwayguy.betterquesting.client.GLManager.glContext().blend(true);
        funwayguy.betterquesting.client.GLManager.glContext().blendFunc(770,771);
        funwayguy.betterquesting.resources.ResourceLibrary.NONE.bind();
        var vertices=funwayguy.betterquesting.client.render.Renderer.get().getVertexBuilder();
        vertices.begin(7);
        gradient(vertices,x,y,w,h*.4f,depth+.01f,.17f,0f);
        gradient(vertices,x,y+h*.4f,w,h*.6f,depth+.01f,0f,.59f);
        vertices.end();
    }
    private static void gradient(funwayguy.betterquesting.client.buffers.VertexBuilder v,float x,float y,float w,float h,float z,float top,float bottom){
        v.vert(x,y+h,z).uv(0f,1f).color(0f,0f,0f,bottom).endVert();
        v.vert(x+w,y+h,z).uv(1f,1f).color(0f,0f,0f,bottom).endVert();
        v.vert(x+w,y,z).uv(1f,0f).color(0f,0f,0f,top).endVert();
        v.vert(x,y,z).uv(0f,0f).color(0f,0f,0f,top).endVert();
    }
}
