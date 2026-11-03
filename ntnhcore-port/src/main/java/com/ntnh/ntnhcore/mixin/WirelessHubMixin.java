package com.ntnh.ntnhcore.mixin;

import appeng.blockentity.grid.AENetworkBlockEntity;
import com.glodblock.github.extendedae.common.me.wireless.WirelessConnect;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessHub;
import com.ntnh.ntnhcore.LegacyWireless;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TileWirelessHub.class, remap=false)
public abstract class WirelessHubMixin {
    @Shadow @Final private WirelessConnect[] connect;
    @Shadow private double powerUse;
    @Inject(method="updatePowerUsage",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$legacySumOfLinks(CallbackInfo ci) {
        powerUse=0D;
        for(var link:connect)if(link.isConnected())powerUse+=LegacyWireless.power(link.getDistance());
        ((AENetworkBlockEntity)(Object)this).getMainNode().setIdlePowerUsage(powerUse);
        ci.cancel();
    }
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void ntnh$noUnlinkedIdleDrain(CallbackInfo ci) {
        powerUse=0D;((AENetworkBlockEntity)(Object)this).getMainNode().setIdlePowerUsage(0D);
    }
}
