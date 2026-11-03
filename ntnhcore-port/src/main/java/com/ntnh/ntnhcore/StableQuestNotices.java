package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.WindowManager;
import funwayguy.betterquesting.client.gui.events.PEventBroadcaster;
import funwayguy.betterquesting.client.gui.widgets.GuiNoticeFeed;

/** Keep one refresh listener for the dedicated notice overlay across repeated refreshes. */
final class StableQuestNotices extends GuiNoticeFeed {
    private funwayguy.betterquesting.client.NoticeFeed.Notice sounded;
    private void playNotice(){
        var notice=funwayguy.betterquesting.client.NoticeFeed.get().peekQueue();
        if(notice!=null&&notice!=sounded&&notice.hasStarted()){
            sounded=notice;BookSounds.play(notice.sound,0.7f);
        }
    }
    @Override public void initPanel(){
        var overlay=WindowManager.get().getOverlay();
        if(overlay!=null){
            PEventBroadcaster.get().clearEvents(overlay);
            PEventBroadcaster.get().setWindowCtx(overlay);
        }
        super.initPanel();
        PEventBroadcaster.get().setWindowCtx(WindowManager.get().getActive());
        playNotice();
    }
    @Override public void drawPanel(int mx,int my,float tick){super.drawPanel(mx,my,tick);playNotice();}
}
