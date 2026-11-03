package com.ntnh.ntnhcore;

import funwayguy.betterquesting.resources.ResourceLibrary;
import funwayguy.betterquesting.resources.loaders.RSetJar;

/** BQ's own resource library does not automatically scan other mods' Minecraft assets. */
final class ResourceLibraryAssets {
    static void install(){ResourceLibrary.get().getJarLoader().addJar(new RSetJar("ntnhcore",ResourceLibraryAssets.class));}
}
