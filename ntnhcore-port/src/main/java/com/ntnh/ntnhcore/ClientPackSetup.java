package com.ntnh.ntnhcore;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Ex Compressum initializes its provider on ServerStarted; remote clients need it before JEI. */
@Mod.EventBusSubscriber(modid="ntnhcore",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClientPackSetup {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->funwayguy.betterquesting.client.layouts.LayoutRegistry.get().setDefBuilder(
            funwayguy.betterquesting.client.layouts.presets.PresetGui.HOME,args->new SecondDawnHome()));
        event.enqueueWork(()->funwayguy.betterquesting.client.layouts.LayoutRegistry.get().setDefBuilder(
            funwayguy.betterquesting.client.layouts.presets.PresetGui.CHAPTERS,args->new LegacyChapters(args.parent())));
        event.enqueueWork(()->funwayguy.betterquesting.client.layouts.LayoutRegistry.get().setDefBuilder(
            funwayguy.betterquesting.client.layouts.presets.PresetOverlay.MAIN,args->new StableQuestNotices()));
        event.enqueueWork(()->funwayguy.betterquesting.client.layouts.LayoutRegistry.get().setDefBuilder(
            funwayguy.betterquesting.client.layouts.presets.PresetGui.QUEST,args->new LegacyQuestDetails(args.parent(),args.value())));
        if(!net.minecraftforge.fml.ModList.get().isLoaded("excompressum") || !net.minecraftforge.fml.ModList.get().isLoaded("exdeorum"))return;
        event.enqueueWork(()->{
            try {
                Class.forName("net.blay09.mods.excompressum.forge.compat.exdeorum.ExDeorumAddon").getConstructor().newInstance();
                org.slf4j.LoggerFactory.getLogger("ntnhcore").info("Initialized Ex Deorum provider for Ex Compressum on the client");
            } catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot initialize Ex Compressum client integration",error);}
        });
    }
}
