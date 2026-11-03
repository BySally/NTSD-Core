package com.ntnh.ntnhcore.mixin;

import com.ntnh.ntnhcore.BQShutdownState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.UUID;

/** BQ 4.0.71 stops its network worker before vanilla disconnects remaining players. */
@Mixin(targets="funwayguy.betterquesting.network.handlers.NetProfileSync",remap=false)
public abstract class BQProfileShutdownMixin {
    @Inject(method="sendNames",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ntnh$skipLateBroadcast(UUID[] recipients,Collection<UUID> uuids,Collection<String> names,CallbackInfo callback){
        if(BQShutdownState.skipProfileBroadcast()){
            org.slf4j.LoggerFactory.getLogger("ntnhcore").info("Skipped BetterQuesting profile broadcast during server shutdown");
            callback.cancel();
        }
    }
}
