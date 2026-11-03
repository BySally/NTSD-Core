package com.ntnh.ntnhcore;

import com.google.gson.JsonParser;
import funwayguy.betterquesting.client.gui.IGuiCanvas;
import funwayguy.betterquesting.client.gui.controls.PanelButtonQuest;
import funwayguy.betterquesting.client.gui.panels.content.CanvasChapter;
import funwayguy.betterquesting.client.gui.screens.GuiChapters;
import funwayguy.betterquesting.enums.EQuestState;
import funwayguy.betterquesting.properties.CommonProps;
import funwayguy.betterquesting.questing.chapters.ChapterDatabase;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Keep native BQ controls, permissions and quest actions, with a readable branch view. */
final class LegacyChapters extends GuiChapters {
    private static final Set<Integer> STACKED=loadChapters();
    private static final Map<Integer,View> VIEWS=new HashMap<>();
    private CanvasChapter graph;
    private PanelButtonQuest firstButton;
    private int chapterID=-1;
    private boolean progressRequested;
    private record View(float zoom,int x,int y){}
    LegacyChapters(IGuiCanvas parent){super(parent);changeBG(new QuestBackdrop());}

    @Override public void initPanel(){
        // BQ's bulk login sync sends definitions/cache but omits unchanged progress.
        // Request the native party-aware progress packet once per screen, including on reconnect.
        if(!progressRequested){
            progressRequested=true;
            funwayguy.betterquesting.network.handlers.NetProgressSync.requestProgress();
        }
        super.initPanel();
        graph=findGraph(this);firstButton=null;chapterID=-1;
        BookSounds.registerClicks();
    }
    @Override public void drawPanel(int mouseX,int mouseY,float partialTick){
        if(graph==null)graph=findGraph(this);
        if(graph!=null&&graph.getQuestLine()!=null&&!graph.getQuestButtons().isEmpty()){
            int id=ChapterDatabase.INSTANCE.getID(graph.getQuestLine());
            var first=graph.getQuestButtons().iterator().next();
            if(STACKED.contains(id)&&graph.getTransform().getWidth()>120&&graph.getTransform().getHeight()>100){
                if(id!=chapterID||first!=firstButton){
                    View previous=VIEWS.get(id);
                    if(previous!=null)restore(previous);
                    else initialView();
                    chapterID=id;firstButton=first;
                }
            }else{chapterID=-1;firstButton=null;}
        }else{chapterID=-1;firstButton=null;}
        LegacyQuestLines.chapter(graph!=null&&graph.getQuestLine()!=null?ChapterDatabase.INSTANCE.getID(graph.getQuestLine()):-1);
        super.drawPanel(mouseX,mouseY,partialTick);
        if(graph!=null&&chapterID>=0)VIEWS.put(chapterID,new View(graph.getZoom(),graph.getScrollX(),graph.getScrollY()));
    }
    private void initialView(){
        var progress=ProgressManager.get().getProgress(ProfileManager.get().getLocal().userID);
        var candidates=graph.getQuestButtons().stream().filter(button->{
            var quest=button.getStoredValue().getQuest();return quest!=null&&quest.getInfo().getProp(CommonProps.MAIN);
        }).toList();
        if(candidates.isEmpty())candidates=new ArrayList<>(graph.getQuestButtons());
        var unlocked=candidates.stream().filter(button->{
            var state=progress.getQuestState(button.getStoredValue().getQuestID());
            return state==EQuestState.UNLOCKED||state==EQuestState.UNCLAIMED||state==EQuestState.REPEATING;
        }).toList();
        var target=(unlocked.isEmpty()?candidates:unlocked).stream().min(
            Comparator.comparingInt((PanelButtonQuest b)->b.getTransform().getY()).thenComparingInt(b->b.getTransform().getX())).orElseThrow().getTransform();
        graph.setZoom(1f);
        restore(new View(1f,target.getX()+target.getWidth()/2-graph.getTransform().getWidth()/2,target.getY()-24));
    }
    private void restore(View view){
        graph.setZoom(view.zoom());var bounds=graph.getScrollBounds();
        graph.setScrollX(Math.max(bounds.getX(),Math.min(bounds.getX()+bounds.getWidth(),view.x())));
        graph.setScrollY(Math.max(bounds.getY(),Math.min(bounds.getY()+bounds.getHeight(),view.y())));
        graph.updatePanelScroll();
    }
    private static CanvasChapter findGraph(IGuiCanvas canvas){
        for(var panel:canvas.getChildren()){
            if(panel instanceof CanvasChapter found)return found;
            if(panel instanceof IGuiCanvas child){var found=findGraph(child);if(found!=null){
                if(panel instanceof funwayguy.betterquesting.client.gui.panels.CanvasResizeable frame)frame.changeBG(new QuestBackdrop(funwayguy.betterquesting.client.themes.presets.PresetTexture.CHAPTER_FRAME));
                return found;
            }}
        }
        return null;
    }
    private static Set<Integer> loadChapters(){
        try(var stream=LegacyChapters.class.getResourceAsStream("/ntnhcore/branch-layout.json")){
            if(stream==null)return Set.of();
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            Set<Integer> ids=new HashSet<>();json.getAsJsonArray("chapters").forEach(value->ids.add(value.getAsInt()));return Set.copyOf(ids);
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot read NTNH branch layout",error);}
    }
}
