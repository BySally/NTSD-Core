package com.ntnh.ntnhcore.mixin;

import com.ntnh.ntnhcore.LegacyBackpacks;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BackpackWrapper.class,remap=false)
public abstract class LegacyBackpackCapacityMixin {
    @Shadow protected ItemStack stack;
    @Shadow private int tanksCapacity;
    // This native initialization method runs before storage sizes and handlers are read.
    // Hook the ordinary method: this pinned Mixin runtime only permits constructor injections at RETURN.
    @Inject(method="initializeSleepingBag",at=@At("HEAD"),remap=false)
    private void ntnh$freshLootBag(ItemStack bag,CallbackInfo ci){
        if(bag.getItem() instanceof com.tiviacz.travelersbackpack.items.TravelersBackpackItem
            &&com.tiviacz.travelersbackpack.config.TravelersBackpackConfig.serverSpec.isLoaded()&&(!bag.hasTag()||
            (!bag.getTag().contains("StorageSlots")&&!bag.getTag().contains("Inventory")&&!bag.getTag().contains("Upgrades")&&!bag.getTag().contains("Tier"))))
            LegacyBackpacks.initializeFresh(bag);
    }
    @Inject(method="setBackpackTankCapacity",at=@At("RETURN"),remap=false)
    private void ntnh$oldCapacity(CallbackInfo ci){
        // Native capacities multiply an integer per-row value; 48 slots mean six rows,
        // so a config alone cannot represent exactly 4000 mB. Never shrink existing modern bags.
        if(LegacyBackpacks.legacy(stack))tanksCapacity=4000;
    }
}
