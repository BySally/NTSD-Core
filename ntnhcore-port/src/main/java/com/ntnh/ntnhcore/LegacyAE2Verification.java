package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import java.util.List;

/** Loaded-server checks for the restored AE2 progression; gated by the validation flag. */
final class LegacyAE2Verification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static CraftingContainer grid(){return new TransientCraftingContainer(new AbstractContainerMenu(null,0){
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
        @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int slot){return ItemStack.EMPTY;}
    },3,3);}
    static void run(MinecraftServer server,JsonObject report){
        int checked=0;
        for(int number:new int[]{71,72,74,75,76,78,88,98}){
            var id=new ResourceLocation("ntnhcore",String.format("migrated/ae2base/%03d",number));
            var recipe=(CraftingRecipe)server.getRecipeManager().byKey(id).orElseThrow();
            var shape=(ShapedRecipe)recipe;
            // Check the same crafting matcher the player uses, including every
            // modern material variant accepted by a migrated legacy ore tag.
            for(int changed=0;changed<shape.getIngredients().size();changed++){
                var ingredient=shape.getIngredients().get(changed);
                for(var variant:ingredient.getItems()){
                    var grid=grid();
                    for(int i=0;i<shape.getIngredients().size();i++){
                        var values=shape.getIngredients().get(i).getItems();
                        if(values.length>0)grid.setItem(i/shape.getWidth()*3+i%shape.getWidth(),(i==changed?variant:values[0]).copy());
                    }
                    require(recipe.matches(grid,server.overworld()),"Restored AE2 recipe rejects tag variant "+id+" "+variant);
                    var actual=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,server.overworld()).orElseThrow();
                    require(ItemStack.matches(actual.assemble(grid,server.registryAccess()),recipe.assemble(grid,server.registryAccess())),"Conflicting recipe wins over "+id+": "+actual.getId());
                    // An arbitrary cheap ingredient must not bypass the old cost.
                    grid.setItem(changed/shape.getWidth()*3+changed%shape.getWidth(),new ItemStack(Items.DIRT));
                    require(!recipe.matches(grid,server.overworld()),"Dirt bypasses the restored AE2 cost "+id);checked++;
                }
            }
        }
        report.addProperty("legacy_ae2_crafting_variant_checks",checked);
        int samples=0;
        for(boolean charged:new boolean[]{false,true}){
            var id=new ResourceLocation("ntnhcore",(charged?"charged_":"")+"certus_quartz_ore");
            var state=BuiltInRegistries.BLOCK.getOptional(id).orElseThrow().defaultBlockState();
            require(state.is(BlockTags.MINEABLE_WITH_PICKAXE),"Certus ore has no pickaxe tag");
            require(!ItemStack.EMPTY.isCorrectToolForDrops(state),"Bare hand can harvest certus ore");
            require(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),"Wood pickaxe cannot harvest legacy tier-0 certus ore");
            require(new ItemStack(LegacyFlintTools.pickaxe.get()).isCorrectToolForDrops(state),"NTNH flint pickaxe cannot harvest old certus ore");
            String drop="ae2:"+(charged?"charged_":"")+"certus_quartz_crystal";
            for(int fortune:new int[]{0,3}){
                var tool=new ItemStack(Items.DIAMOND_PICKAXE);if(fortune>0)tool.enchant(Enchantments.BLOCK_FORTUNE,fortune);
                int min=999,max=0;
                for(int i=0;i<256;i++){
                    var drops=Block.getDrops(state,server.overworld(),BlockPos.ZERO,null,null,tool);
                    require(drops.size()==1&&BuiltInRegistries.ITEM.getKey(drops.get(0).getItem()).toString().equals(drop),"Wrong certus ore drop "+id);
                    int amount=drops.get(0).getCount();min=Math.min(min,amount);max=Math.max(max,amount);samples++;
                    require(amount>=1&&amount<=(fortune==0?2:8),"Certus ore fortune differs from old range");
                }
                require(min==1&&max==(fortune==0?2:8),"Certus drop range not exercised");
            }
            var silk=new ItemStack(Items.DIAMOND_PICKAXE);silk.enchant(Enchantments.SILK_TOUCH,1);
            var drops=Block.getDrops(state,server.overworld(),BlockPos.ZERO,null,null,silk);
            require(drops.size()==1&&drops.get(0).getCount()==1&&BuiltInRegistries.ITEM.getKey(drops.get(0).getItem()).equals(id),"Silk Touch loses legacy ore identity");
            require(state.getBlock().getExpDrop(state,server.overworld(),server.overworld().getRandom(),BlockPos.ZERO,0,1)==0,"Silk Touch creates certus XP");
            for(int i=0;i<16;i++){
                int xp=state.getBlock().getExpDrop(state,server.overworld(),server.overworld().getRandom(),BlockPos.ZERO,0,0);
                require(xp>=2&&xp<=5,"Legacy certus XP is outside 2..5");
            }
        }
        report.addProperty("legacy_certus_drop_samples",samples);
        report.addProperty("legacy_certus_silk_touch_and_harvest_tier_checked",true);
    }
}
