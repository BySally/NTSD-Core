package com.ntnh.ntnhcore.mixin;

import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.capabilities.BasicDrawerAttributes;
import com.ntnh.ntnhcore.LegacyDrawerTape;
import org.spongepowered.asm.mixin.*;

/** Feed the actual seal into native cached attributes, controllers and item handlers. */
@Mixin(targets="com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers$DrawerAttributes",remap=false)
public abstract class DrawerTapeAttributesMixin extends BasicDrawerAttributes implements LegacyDrawerTape.NativeSuspension {
    @Shadow @Final private BlockEntityDrawers this$0;
    @Shadow protected abstract void onAttributeChanged();
    @Override public boolean isSealed(){return LegacyDrawerTape.sealed(this$0.getBlockState());}
    @Override public boolean isSuspended(){return isSealed()||super.isSuspended();}
    @Override public boolean ntnh$rawSuspended(){return super.isSuspended();}
    @Override public boolean setIsSealed(boolean value){
        if(!LegacyDrawerTape.change(this$0,value))return false;
        onAttributeChanged();return true;
    }
}
