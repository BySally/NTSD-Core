package com.ntnh.ntnhcore.mixin;

import com.glodblock.github.extendedae.common.me.wireless.WirelessConnect;
import com.ntnh.ntnhcore.LegacyWireless;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=WirelessConnect.class,remap=false)
public abstract class WirelessRangeMixin {
    @Redirect(method="updateStatus",at=@At(value="FIELD",target="Lcom/glodblock/github/extendedae/config/EPPConfig;wirelessMaxRange:D"),remap=false)
    private double ntnh$legacyRange(){return LegacyWireless.range();}
}
