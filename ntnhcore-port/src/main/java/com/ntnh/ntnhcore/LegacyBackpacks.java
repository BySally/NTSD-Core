package com.ntnh.ntnhcore;

import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.items.upgrades.TanksUpgradeItem;
import com.tiviacz.travelersbackpack.util.NbtHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.List;

/** Old acquisition costs with the pinned, unmodified Traveler's Backpack implementation. */
public final class LegacyBackpacks {
    public static final String MARKER="ntnh_legacy_backpack";
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,"ntnhcore");
    static final RegistryObject<RecipeSerializer<?>> RECIPE=SERIALIZERS.register("legacy_backpack",Serializer::new);
    public static void register(IEventBus bus){SERIALIZERS.register(bus);}
    public static void initializeFresh(ItemStack stack){
        var tag=stack.getOrCreateTag();
        tag.putBoolean(MARKER,true);
        // Fresh legacy-recipe/loot bags must also be correct in existing worlds whose
        // Forge serverconfig still has the native 27-slot default. Never resize existing bags.
        tag.putInt("Tier",0);
        tag.putInt("StorageSlots",48);
        tag.putInt("UpgradeSlots",2);
        tag.putInt("ToolSlots",2);
        tag.putInt("SleepingBagColor",14);
        // Store native handlers directly. Upstream StarterUpgrades installs modules only
        // after UpgradeManager construction and can leave them inactive until another open.
        var modules=new net.minecraftforge.items.ItemStackHandler(2);
        modules.setStackInSlot(0,new ItemStack(com.tiviacz.travelersbackpack.init.ModItems.TANKS_UPGRADE.get()));
        modules.setStackInSlot(1,new ItemStack(com.tiviacz.travelersbackpack.init.ModItems.CRAFTING_UPGRADE.get()));
        NbtHelper.set(stack,"Upgrades",modules);
        NbtHelper.set(stack,"RenderInfo",TanksUpgradeItem.writeToRenderData());
    }
    public static boolean legacy(ItemStack stack){return stack.hasTag()&&stack.getTag().getBoolean(MARKER);}

    public static final class Recipe extends ShapedRecipe {
        Recipe(ShapedRecipe base){super(base.getId(),base.getGroup(),base.category(),base.getWidth(),base.getHeight(),base.getIngredients(),base.getResultItem(RegistryAccess.EMPTY),base.showNotification());}
        @Override public ItemStack assemble(CraftingContainer input,RegistryAccess registries){
            var result=getResultItem(registries).copy();
            ItemStack source=ItemStack.EMPTY;
            for(int i=0;i<input.getContainerSize();i++){
                var item=input.getItem(i);
                if(item.getItem() instanceof com.tiviacz.travelersbackpack.items.TravelersBackpackItem){source=item;break;}
            }
            if(source.isEmpty())initializeFresh(result);
            else {
                CompoundTag merged=source.hasTag()?source.getTag().copy():new CompoundTag();
                if(result.hasTag())merged.merge(result.getTag());
                result.setTag(merged);
                // No initialization of an existing bag: contents, upgrades, tank amounts and size survive reskinning.
            }
            return result;
        }
        @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer input){
            var remaining=super.getRemainingItems(input);
            if(getId().getPath().equals("legacy_backpack/dragon"))for(int i=0;i<input.getContainerSize();i++)
                if(input.getItem(i).is(Items.DRAGON_EGG)){var egg=input.getItem(i).copy();egg.setCount(1);remaining.set(i,egg);}
            return remaining;
        }
        @Override public RecipeSerializer<?> getSerializer(){return RECIPE.get();}
    }
    private static final class Serializer implements RecipeSerializer<Recipe>{
        private final ShapedRecipe.Serializer delegate=new ShapedRecipe.Serializer();
        @Override public Recipe fromJson(ResourceLocation id,com.google.gson.JsonObject json){return new Recipe(delegate.fromJson(id,json));}
        @Override public Recipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer){return new Recipe(delegate.fromNetwork(id,buffer));}
        @Override public void toNetwork(FriendlyByteBuf buffer,Recipe recipe){delegate.toNetwork(buffer,recipe);}
    }
}
