package com.ntnh.ntnhcore;

import funwayguy.betterquesting.client.gui.events.PEventBroadcaster;
import funwayguy.betterquesting.client.gui.events.types.PEventButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Play the sound carried by a native BQ notice; never infer completions from login data. */
final class BookSounds {
    private BookSounds(){}
    static void registerClicks(){
        PEventBroadcaster.get().register((PEventButton event)->{
            if(event.getButton().isActive())play("minecraft:ui.button.click",0.35f);
        },PEventButton.class);
    }
    static void play(String id,float volume){
        if(id==null||id.isBlank())return;
        if(id.equals("random.levelup"))id="minecraft:entity.player.levelup";
        var location=ResourceLocation.tryParse(id);if(location==null)return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(location),1f,volume));
        if(Boolean.getBoolean("ntnhcore.validation"))org.slf4j.LoggerFactory.getLogger("ntnhcore").info("NTNH book sound: {}",location);
    }
}
