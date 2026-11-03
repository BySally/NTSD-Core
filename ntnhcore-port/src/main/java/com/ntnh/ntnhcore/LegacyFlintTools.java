package com.ntnh.ntnhcore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** Required Improving Minecraft early-game tools, reconstructed from the supplied 1.7.10 jar. */
final class LegacyFlintTools {
    static final Tier TIER=TierSortingRegistry.registerTier(
        new ForgeTier(0,59,3.0f,1.0f,8,
            TagKey.create(Registries.BLOCK,new ResourceLocation("ntnhcore","needs_flint_tool")),
            ()->Ingredient.of(Items.FLINT)),
        new ResourceLocation("ntnhcore","flint"),List.of(Tiers.WOOD),List.of(Tiers.STONE));
    static RegistryObject<Item> pickaxe,shovel,hatchet;

    static void register(DeferredRegister<Item> items){
        pickaxe=items.register("flint_pickaxe",()->new PickaxeItem(TIER,2,-2.4f,new Item.Properties()));
        shovel=items.register("flint_shovel",()->new ShovelItem(TIER,1,-3.0f,new Item.Properties()));
        hatchet=items.register("flint_hatchet",()->new AxeItem(TIER,3,-3.2f,new Item.Properties()));
    }
}
