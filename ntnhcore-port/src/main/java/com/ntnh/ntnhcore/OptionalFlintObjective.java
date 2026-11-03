package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.IGuiPanel;
import funwayguy.betterquesting.client.gui.panels.content.PanelTextBox;
import funwayguy.betterquesting.client.misc.IGuiRect;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.progress.PlayerProgress;
import funwayguy.betterquesting.questing.player.progress.ProgressManager;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.tasks.Task;
import funwayguy.betterquesting.registry.ResourcePath;
import funwayguy.xbt.std.XBTTagCompound;
import net.minecraft.server.level.ServerPlayer;

/** BQ 4 has no optional-task contract. Track the optional acquisition separately from its gate. */
final class OptionalFlintObjective extends Task {
    @Override public ResourcePath getID(){return new ResourcePath("ntnhcore:optional_flint_shovel");}
    @Override public String getNameKey(){return "ntnhcore.ui.flint_optional";}
    static boolean update(ServerPlayer player,PlayerProgress progress,int quest,int task){
        var data=progress.getTaskData(quest,task);
        boolean acquired=data.getBoolean("optional_acquired")||player.getInventory().countItem(LegacyFlintTools.shovel.get())>0;
        boolean changed=!data.getBoolean("isComplete")||acquired!=data.getBoolean("optional_acquired");
        data.setBoolean("optional_acquired",acquired);
        if(!data.getBoolean("isComplete"))progress.setTaskDone(quest,task,true);
        return changed;
    }
    @Override public void detect(ParticipantInfo participant,int task,int quest){
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        var player=server.getPlayerList().getPlayer(participant.PLAYER.playerID);if(player==null)return;
        if(update(player,participant.PLAYER.progress,quest,task))participant.markDirty(quest);
    }
    @Override public IGuiPanel getPanel(IGuiRect bounds,int task,int quest){
        var progress=ProgressManager.get().getProgress(ProfileManager.get().getLocal().userID);
        boolean acquired=progress.getTaskData(quest,task).getBoolean("optional_acquired");
        return new PanelTextBox(bounds,QuestText.text("ntnhcore.ui.flint_intro")
            +(acquired?QuestText.text("ntnhcore.ui.flint_done"):QuestText.text("ntnhcore.ui.flint_missing"))
            +QuestText.text("ntnhcore.ui.flint_note"));
    }
    @Override public XBTTagCompound writeToXBT(XBTTagCompound data){return data;}
    @Override public void readFromXBT(XBTTagCompound data){}
}
