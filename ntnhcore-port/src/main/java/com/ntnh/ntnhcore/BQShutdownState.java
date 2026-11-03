package com.ntnh.ntnhcore;

/** Profile persistence is left to BQ; only its late, unusable broadcast is skipped. */
public final class BQShutdownState {
    private static volatile net.minecraft.server.MinecraftServer stoppingServer;
    public static void starting(net.minecraftforge.event.server.ServerAboutToStartEvent event){stoppingServer=null;}
    public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){stoppingServer=event.getServer();}
    public static boolean skipProfileBroadcast(){
        return stoppingServer!=null&&net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()==stoppingServer;
    }
}
