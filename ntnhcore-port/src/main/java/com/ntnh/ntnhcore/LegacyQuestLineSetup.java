package com.ntnh.ntnhcore;

import funwayguy.betterquesting.registry.ResourcePath;
import funwayguy.betterquesting.resources.ResourceFunctions;

/** Register before BQ's first theme reload, without initializing GL-backed line classes. */
final class LegacyQuestLineSetup {
    static void install(){
        LegacyDrawerTapeClient.install();
        LegacyWirelessClient.install();
        LegacyBackpackClient.install();
        ResourceLibraryAssets.install();
        ResourceFunctions.INSTANCE.getLineReg().register(
            new ResourcePath("betterquesting:line_directional"),LegacyQuestLines::new);
        ResourceFunctions.INSTANCE.getTexReg().register(
            new ResourcePath("ntnhcore:quest_state"),LegacyQuestStateIcon::new);
    }
}
