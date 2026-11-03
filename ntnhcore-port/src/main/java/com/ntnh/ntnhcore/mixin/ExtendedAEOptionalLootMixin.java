package com.ntnh.ntnhcore.mixin;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned ExtendedAE ships the ProjectE block's table even without the block. */
@Mixin(value=ForgeHooks.class,remap=false)
public abstract class ExtendedAEOptionalLootMixin {
    @Inject(method="loadLootTable",at=@At("HEAD"),cancellable=true,remap=false)
    private static void ntnh$absentOptionalBlock(Gson gson,ResourceLocation name,JsonElement data,boolean custom,CallbackInfoReturnable<LootTable> ci){
        if(!name.getNamespace().equals("expatternprovider")||!name.getPath().equals("blocks/ex_emc_interface"))return;
        if(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("expatternprovider","ex_emc_interface")))return;
        var empty=LootTable.lootTable().build();empty.setLootTableId(name);empty.freeze();ci.setReturnValue(empty);
    }
}
