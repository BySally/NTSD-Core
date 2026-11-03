package com.ntnh.ntnhcore;

/** Reproduce the native late broadcast after BQ has stopped its worker.
 * Only enabled in the isolated test process, never on an ordinary server. */
final class BQShutdownVerification {
    static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){
        if(!Boolean.getBoolean("ntnhcore.validation"))return;
        var report=new com.google.gson.JsonObject();
        boolean ready=funwayguy.betterquesting.utils.workers.WorkerUtil.NETWORK.isReady();
        report.addProperty("bq_network_worker_ready",ready);
        report.addProperty("connected_players_at_stop",event.getServer().getPlayerList().getPlayerCount());
        try{
            if(ready)throw new IllegalStateException("Shutdown check ran before the BQ worker stopped");
            // Without the guard this real BQ call throws from WorkerThread.enqueue.
            funwayguy.betterquesting.network.handlers.NetProfileSync.sendNames(null,java.util.List.of(java.util.UUID.fromString("9ed5763d-5a97-3f07-ac20-93c1883cf424")),null);
            report.addProperty("native_late_profile_broadcast_safe",true);report.addProperty("passed",true);
        }catch(Exception e){report.addProperty("passed",false);report.addProperty("error",e.toString());}
        try{
            java.nio.file.Files.writeString(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ntnhcore/shutdown-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
