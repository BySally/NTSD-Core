package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import funwayguy.betterquesting.client.gui.resources.colors.GuiColorStatic;
import funwayguy.betterquesting.client.gui.resources.colors.IGuiColor;
import funwayguy.betterquesting.client.gui.resources.textures.GuiTextureMesh;
import funwayguy.betterquesting.client.gui.resources.textures.IGuiTexture;
import funwayguy.betterquesting.client.math.Vec4f;
import java.util.ArrayList;

/** Theme-only checkbox artwork. BQ retains ownership of clicks and saved task progress. */
final class LegacyQuestStateIcon implements IGuiTexture {
    private final GuiTextureMesh shape;
    private final IGuiColor color;

    LegacyQuestStateIcon(JsonObject json) {
        boolean complete="complete".equals(json.get("state").getAsString());
        color=new GuiColorStatic(complete?0xff55ff55:0xffff5555);
        var vertices=new ArrayList<Vec4f>();
        if(complete){
            bar(vertices,.16f,.50f,.40f,.75f,.16f);
            bar(vertices,.40f,.75f,.86f,.22f,.16f);
        }else{
            bar(vertices,.22f,.22f,.78f,.78f,.16f);
            bar(vertices,.22f,.78f,.78f,.22f,.16f);
        }
        shape=new GuiTextureMesh(4,vertices.toArray(Vec4f[]::new)); // GL_TRIANGLES
    }

    private static void bar(ArrayList<Vec4f> out,float x1,float y1,float x2,float y2,float width){
        float length=(float)Math.hypot(x2-x1,y2-y1);
        float nx=-(y2-y1)/length*width/2,ny=(x2-x1)/length*width/2;
        Vec4f a=new Vec4f(x1+nx,y1+ny,0,0),b=new Vec4f(x2+nx,y2+ny,0,0);
        Vec4f c=new Vec4f(x2-nx,y2-ny,0,0),d=new Vec4f(x1-nx,y1-ny,0,0);
        out.add(a);out.add(b);out.add(c);out.add(a);out.add(c);out.add(d);
    }

    @Override public void drawTexture(int x,int y,int width,int height,float z,float partial){
        shape.drawTexture(x,y,width,height,z,partial,color);
    }
    @Override public void drawTexture(int x,int y,int width,int height,float z,float partial,IGuiColor tint){
        // A completed checkbox is disabled; keep its confirmation visibly green.
        drawTexture(x,y,width,height,z,partial);
    }
}
