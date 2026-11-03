package com.ntnh.ntnhcore.mixin;

import com.texelsaurus.minecraft.chameleon.service.ChameleonConfig.ConfigEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** The old "merge" policy drops all material; never silently void the remainder after 32 stacks. */
@Mixin(targets="com.jaquadro.minecraft.storagedrawers.inventory.DrawerInventoryHelper",remap=false)
public abstract class DrawerContentsDropMixin {
    @Redirect(method="dropInventoryItems",at=@At(value="INVOKE",target="Lcom/texelsaurus/minecraft/chameleon/service/ChameleonConfig$ConfigEntry;get()Ljava/lang/Object;"),remap=false)
    private static Object ntnh$dropEveryStack(ConfigEntry<?> entry){return Integer.MAX_VALUE;}
}
