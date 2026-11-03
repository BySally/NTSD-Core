package com.ntnh.ntnhcore;

import com.google.gson.JsonParser;
import funwayguy.betterquesting.questing.chapters.ChapterDatabase;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** BQ's per-file loader omits ordering; restore the original NTNH chapter sequence after loading. */
final class BQChapterOrder {
    private static final int[] ORDER=load();
    static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null || ChapterDatabase.INSTANCE.size()==0)return;
        var ids=ORDER;
        // The BQ file loader may replace the database after an earlier server tick.
        // Wait for every chapter, and also repair order after a subsequent reload.
        for(int id:ids)if(ChapterDatabase.INSTANCE.getValue(id)==null)return;
        for(int i=0;i<ids.length;i++)if(ChapterDatabase.INSTANCE.getOrderIndex(ids[i])!=i)
            ChapterDatabase.INSTANCE.setOrderIndex(ids[i],i);
    }
    static int[] read(){
        return ORDER.clone();
    }
    private static int[] load(){
        try(var stream=BQChapterOrder.class.getResourceAsStream("/ntnhcore/chapter-order.json")){
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray();
            int[] ids=new int[json.size()];for(int i=0;i<ids.length;i++)ids[i]=json.get(i).getAsInt();return ids;
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
