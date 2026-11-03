package com.ntnh.ntnhcore.mixin;

import com.ntnh.ntnhcore.LegacyDrawerTape;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributesModifiable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guard direct native transfer paths too, including modern built-in hopper/magnet upgrades. */
@Mixin(targets="com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers",remap=false)
public abstract class DrawerTapeEntityMixin {
    @Redirect(method="writePortable",at=@At(value="INVOKE",target="Lcom/jaquadro/minecraft/storagedrawers/api/storage/IDrawerAttributesModifiable;isSuspended()Z"),remap=false)
    private boolean ntnh$doNotPersistTemporarySealAsSuspension(IDrawerAttributesModifiable attrs){
        return attrs instanceof LegacyDrawerTape.NativeSuspension raw?raw.ntnh$rawSuspended():attrs.isSuspended();
    }
    @Inject(method="putItemsIntoSlot(ILnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/player/Player;)I",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noPackedInput(int slot,ItemStack stack,int count,Player player,CallbackInfoReturnable<Integer> ci){
        if(LegacyDrawerTape.sealed(((BlockEntity)(Object)this).getBlockState()))ci.setReturnValue(0);
    }
    @Inject(method="takeItemsFromSlot(IILnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noPackedOutput(int slot,int count,Player player,CallbackInfoReturnable<ItemStack> ci){
        if(LegacyDrawerTape.sealed(((BlockEntity)(Object)this).getBlockState()))ci.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="interactReplaceDrawer(ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noPackedReplacement(int slot,ItemStack stack,Player player,CallbackInfoReturnable<Boolean> ci){
        if(LegacyDrawerTape.sealed(((BlockEntity)(Object)this).getBlockState()))ci.setReturnValue(false);
    }
}
