package com.ntnh.ntnhcore;

import com.google.gson.*;
import funwayguy.betterquesting.client.gui.resources.lines.*;
import funwayguy.betterquesting.client.math.Vec2f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Thin obstacle-aware wires, with bridge gaps and one directional tip. */
final class LegacyQuestLines implements IGuiLine {
    private record Route(List<Vec2f> points,List<List<Vec2f>> strokes){}
    private static final Map<Integer,Map<String,Route>> ROUTES=load();
    private static int chapter=-1;
    private final GuiLineSimple line=new GuiLineSimple(1024,(short)0xffff);
    LegacyQuestLines(JsonObject json){}
    static void chapter(int id){chapter=id;}
    private static Vec2f point(JsonElement value){var a=value.getAsJsonArray();return new Vec2f(a.get(0).getAsFloat(),a.get(1).getAsFloat());}
    private static Map<Integer,Map<String,Route>> load(){
        try(var input=LegacyQuestLines.class.getResourceAsStream("/ntnhcore/quest-routes.json")){
            if(input==null)return Map.of();var json=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Integer,Map<String,Route>> chapters=new HashMap<>();
            for(var chapter:json.entrySet()){
                Map<String,Route> routes=new HashMap<>();
                for(var entry:chapter.getValue().getAsJsonObject().entrySet()){
                    var data=entry.getValue().getAsJsonObject();List<Vec2f> points=new ArrayList<>();List<List<Vec2f>> strokes=new ArrayList<>();
                    data.getAsJsonArray("points").forEach(p->points.add(point(p)));
                    data.getAsJsonArray("strokes").forEach(p->{var a=p.getAsJsonArray();strokes.add(List.of(point(a.get(0)),point(a.get(1))));});
                    routes.put(entry.getKey(),new Route(points,strokes));
                }
                chapters.put(Integer.parseInt(chapter.getKey()),Map.copyOf(routes));
            }
            return Map.copyOf(chapters);
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot load quest wire routes",error);}
    }
    @Override public void drawLine(Vec2f start,Vec2f end,float width,float offset,float z,int color){
        String key=Math.round(start.getX()*2)+","+Math.round(start.getY()*2)+","+Math.round(end.getX()*2)+","+Math.round(end.getY()*2);
        Route route=ROUTES.getOrDefault(chapter,Map.of()).get(key);int muted=((int)((color>>>24)*.7f)<<24)|(color&0xffffff);
        List<Vec2f> points;
        if(route==null){line.drawLine(start,end,1,offset,z,muted);points=List.of(start,end);}
        else{for(var stroke:route.strokes())line.drawLine(stroke.get(0),stroke.get(1),1,offset,z,muted);points=route.points();}
        if(points.size()<2)return;var tip=points.get(points.size()-1);var before=points.get(points.size()-2);
        float dx=tip.getX()-before.getX(),dy=tip.getY()-before.getY(),length=(float)Math.hypot(dx,dy);if(length<1)return;dx/=length;dy/=length;
        float size=3;line.drawLine(new Vec2f(tip.getX()-dx*size-dy*size,tip.getY()-dy*size+dx*size),tip,1,offset,z,muted);
        line.drawLine(new Vec2f(tip.getX()-dx*size+dy*size,tip.getY()-dy*size-dx*size),tip,1,offset,z,muted);
    }
}
