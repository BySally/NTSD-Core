package com.ntnh.ntnhcore.mixin;

import appeng.blockentity.grid.AENetworkBlockEntity;
import com.glodblock.github.extendedae.common.me.wireless.WirelessConnect;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;
import com.ntnh.ntnhcore.LegacyWireless;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=TileWirelessConnector.class, remap=false)
public abstract class WirelessConnectorMixin {
    @Shadow @Final private WirelessConnect connect;
    @Shadow private double powerUse;
    @Inject(method="updatePowerUsage",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$legacyPower(CallbackInfo ci) {
        powerUse = connect.isConnected() ? LegacyWireless.power(connect.getDistance()) : 0D;
        ((AENetworkBlockEntity)(Object)this).getMainNode().setIdlePowerUsage(powerUse);
        ci.cancel();
    }
    // The pinned upstream setter does not dirty the chunk; relinking an already saved
    // connector must survive the next ordinary chunk save, without a forced save-all.
    @Inject(method="setFrequency",at=@At("RETURN"),remap=false)
    private void ntnh$persistRelink(long frequency, CallbackInfo ci) {((BlockEntity)(Object)this).setChanged();}
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void ntnh$noUnlinkedIdleDrain(CallbackInfo ci) {
        powerUse=0D;((AENetworkBlockEntity)(Object)this).getMainNode().setIdlePowerUsage(0D);
    }
}
