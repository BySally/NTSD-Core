package com.ntnh.ntnhcore.mixin;

import com.jaquadro.minecraft.storagedrawers.block.FaceSlotBlock.InteractContext;
import com.jaquadro.minecraft.storagedrawers.block.tile.BlockEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.block.tile.tiledata.UpgradeData;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerGroup;
import com.jaquadro.minecraft.storagedrawers.inventory.DrawerInventoryHelper;
import com.jaquadro.minecraft.storagedrawers.config.ModCommonConfig.DropMode;
import com.texelsaurus.minecraft.chameleon.service.ChameleonConfig.ConfigEntry;
import com.ntnh.ntnhcore.LegacyDrawerTape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Pinned SD 12.15.1 runtime names. Never cancel onRemove: native controller cleanup must run. */
@Mixin(targets="com.jaquadro.minecraft.storagedrawers.block.BlockDrawers",remap=false)
public abstract class DrawerTapeBlockMixin extends Block {
    protected DrawerTapeBlockMixin(Properties properties){super(properties);}
    @Inject(method="<init>",at=@At("TAIL"),remap=false)
    private void ntnh$unsealedByDefault(CallbackInfo ci){registerDefaultState(defaultBlockState().setValue(LegacyDrawerTape.SEALED,false));}
    @Inject(method="m_7926_",at=@At("TAIL"),remap=false)
    private void ntnh$sealProperty(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci){builder.add(LegacyDrawerTape.SEALED);}

    @Redirect(method="getMainDrop",at=@At(value="INVOKE",target="Lcom/texelsaurus/minecraft/chameleon/service/ChameleonConfig$ConfigEntry;get()Ljava/lang/Object;"),remap=false)
    private Object ntnh$portableOnlyIfPacked(ConfigEntry<?> entry,BlockState state,BlockEntityDrawers tile){return LegacyDrawerTape.sealed(state)?DropMode.KEEP:DropMode.DROP;}
    @Inject(method="getMainDrop",at=@At("RETURN"),cancellable=true,remap=false)
    private void ntnh$preserveEmptyPackedMetadata(BlockState state,BlockEntityDrawers tile,CallbackInfoReturnable<ItemStack> ci){
        if(tile!=null&&LegacyDrawerTape.sealed(state))ci.getReturnValue().getOrCreateTag().put("tile",tile.saveWithoutMetadata());
    }
    @Redirect(method="m_6810_",at=@At(value="INVOKE",target="Lcom/texelsaurus/minecraft/chameleon/service/ChameleonConfig$ConfigEntry;get()Ljava/lang/Object;"),remap=false)
    private Object ntnh$spillOnlyIfUnpacked(ConfigEntry<?> entry,BlockState oldState,Level level,BlockPos pos,BlockState nextState,boolean moving){
        // The old state is essential: replacement may already have changed the entity's state.
        return LegacyDrawerTape.sealed(oldState)?DropMode.KEEP:DropMode.DROP;
    }
    @Redirect(method="m_6810_",at=@At(value="INVOKE",target="Lcom/jaquadro/minecraft/storagedrawers/inventory/DrawerInventoryHelper;dropUpgradeItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/jaquadro/minecraft/storagedrawers/block/tile/tiledata/UpgradeData;)V"),remap=false)
    private void ntnh$emptyBeforeRemovingCapacity(Level level,BlockPos pos,UpgradeData upgrades){
        // Removing a capacity upgrade first can clamp/void the excess before the content drop.
        // Sample creative vending before removing upgrades too: it must never spill infinite stock.
        if(level.getBlockEntity(pos) instanceof BlockEntityDrawers tile&&!tile.getDrawerAttributes().isUnlimitedVending())
            DrawerInventoryHelper.dropInventoryItems(level,pos,tile.getGroup());
        DrawerInventoryHelper.dropUpgradeItems(level,pos,upgrades);
    }
    @Redirect(method="m_6810_",at=@At(value="INVOKE",target="Lcom/jaquadro/minecraft/storagedrawers/inventory/DrawerInventoryHelper;dropInventoryItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/jaquadro/minecraft/storagedrawers/api/storage/IDrawerGroup;)V"),remap=false)
    private void ntnh$alreadyDroppedContents(Level level,BlockPos pos,IDrawerGroup group){/* Handled before upgrade removal. */}
    @Inject(method={"putSlot","takeSlot"},at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noManualTransfer(InteractContext context,boolean wholeStack,CallbackInfoReturnable<InteractionResult> ci){
        if(LegacyDrawerTape.sealed(context.level,context.pos))ci.setReturnValue(InteractionResult.FAIL);
    }
    @Inject(method="openUI",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noPackedUpgradeMenu(InteractContext context,CallbackInfo ci){if(LegacyDrawerTape.sealed(context.level,context.pos))ci.cancel();}
    @Inject(method="interactPullDrawer",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noDetachedPackedDrawer(InteractContext context,CallbackInfoReturnable<Boolean> ci){if(LegacyDrawerTape.sealed(context.level,context.pos))ci.setReturnValue(false);}
    @Inject(method="interactReturnDrawer",at=@At("HEAD"),cancellable=true,remap=false)
    private void ntnh$noReturnedPackedDrawer(InteractContext context,ItemStack item,CallbackInfoReturnable<Boolean> ci){if(LegacyDrawerTape.sealed(context.level,context.pos))ci.setReturnValue(false);}
}
