package com.ntnh.ntnhcore;

/** Kept separate so the dedicated server never resolves client GUI classes. */
final class QuestBookClient {
    static void open(){
        if(Boolean.getBoolean("ntnhcore.validation")){
            var minecraft=net.minecraft.client.Minecraft.getInstance();
            var local=funwayguy.betterquesting.questing.player.profile.ProfileManager.get().getLocal();
            var progress=funwayguy.betterquesting.questing.player.progress.ProgressManager.get().getProgress(local.userID);
            org.slf4j.LoggerFactory.getLogger("ntnhcore").info("Quest client audit: session={}, entity={}, BQ={}, first={}, profiles={}, progress={}",
                minecraft.getUser().getProfileId(),minecraft.player.getUUID(),local.userID,progress.getQuestState(25089),
                funwayguy.betterquesting.questing.player.profile.ProfileManager.get().getAllProfiles().size(),
                funwayguy.betterquesting.questing.player.progress.ProgressManager.get().getAllProgress().stream().map(p->p.getOwner()+":"+p.getQuestState(25089)).toList());
        }
        funwayguy.betterquesting.client.layouts.presets.PresetGui.HOME.openWindow(
            funwayguy.betterquesting.client.layouts.args.GArgBasic.NONE);
    }
}
