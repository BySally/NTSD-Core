package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.leclowndu93150.chisel.api.IChiselItem;
import com.leclowndu93150.chisel.carving.ChiselMode;
import com.leclowndu93150.chisel.item.ItemChisel;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;

/** Native carving, GUI and task matching for the two restored tiers. Validation runtime only. */
final class LegacyChiselVerification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void run(MinecraftServer server,JsonObject report)throws Exception {
        var level=server.overworld();var fake=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ChiselCheck"));
        ProfileManager.get().makeOrUpdate(fake.getUUID(),"ChiselCheck");var participant=new ParticipantInfo(fake.getUUID());
        var diamond=(ItemChisel)BuiltInRegistries.ITEM.get(new ResourceLocation("chisel:diamond_chisel"));
        var iron=(ItemChisel)BuiltInRegistries.ITEM.get(new ResourceLocation("chisel:iron_chisel"));
        require(new ItemStack(iron).getMaxDamage()==500&&new ItemStack(diamond).getMaxDamage()==5000,"Legacy iron/diamond durability config not applied");
        require(iron.canChiselBlock(level,fake,InteractionHand.MAIN_HAND,level.getSharedSpawnPos(),Blocks.STONE.defaultBlockState()),"Legacy iron left-click setting not applied");
        var helper=Class.forName("com.leclowndu93150.chisel.carving.CarvingHelper");
        var variant=(Block)helper.getMethod("getNextVariant",net.minecraft.world.level.block.state.BlockState.class).invoke(null,Blocks.STONE.defaultBlockState());
        require(variant!=null&&variant!=Blocks.STONE,"Native stone carving group unavailable");
        int modes=0,gui=0;
        var online=server.getPlayerList().getPlayerByName("NTNHNovTest");require(online!=null,"GUI validation needs the connected isolated test client");
        for(boolean star:new boolean[]{false,true}){
            var item=(ItemChisel)(star?LegacyChisels.NETHER_STAR:LegacyChisels.OBSIDIAN).get();var tool=new ItemStack(item);
            int durability=star?32767:2500;require(tool.getMaxDamage()==durability,"Wrong restored chisel durability");
            require(item.isValidRepairItem(tool,new ItemStack(star?Items.NETHER_STAR:Items.OBSIDIAN))&&!item.isValidRepairItem(tool,new ItemStack(Items.DIAMOND)),"Wrong chisel repair material");
            double attack=item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream().mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount).sum();
            require(attack==(star?6:4)&&item.getDefaultAttributeModifiers(EquipmentSlot.OFFHAND).isEmpty(),"Wrong restored attack modifiers");
            for(var mode:ChiselMode.values()){
                require(item.supportsMode(fake,tool,mode)==diamond.supportsMode(fake,new ItemStack(diamond),mode),"Restored chisel mode support differs from native regular modes");
                if(!item.supportsMode(fake,tool,mode))continue;
                item.setMode(tool,mode);require(item.getMode(tool)==mode&&item.getMode(tool.copy())==mode,"Native mode NBT lost");modes++;
            }
            item.setMode(tool,ChiselMode.SINGLE);var input=new ItemStack(Blocks.STONE,8);var target=new ItemStack(variant);
            require(item.canChisel(level,fake,tool,target)&&item.canChiselBlock(level,fake,InteractionHand.MAIN_HAND,level.getSharedSpawnPos(),Blocks.STONE.defaultBlockState()),"Restored tool rejected by native carving API");
            var result=((IChiselItem)item).craftItem(tool,input,target,fake,EquipmentSlot.MAINHAND);
            require(result.is(variant.asItem())&&result.getCount()==8&&input.isEmpty()&&tool.getDamageValue()==8,"Native carving duplicates material or fails tool wear");
            var recipe=(CraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("ntnhcore:legacy_chisel/"+(star?"nether_star_chisel":"obsidian_chisel"))).orElseThrow();
            var shape=(ShapedRecipe)recipe;var grid=new TransientCraftingContainer(new AbstractContainerMenu(null,0){
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
                @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int slot){return ItemStack.EMPTY;}
            },3,3);
            for(int i=0;i<shape.getIngredients().size();i++){var choices=shape.getIngredients().get(i).getItems();if(choices.length>0)grid.setItem(i/shape.getWidth()*3+i%shape.getWidth(),choices[0].copy());}
            require(recipe.matches(grid,level)&&server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow().assemble(grid,server.registryAccess()).is(item),"Old chisel recipe loses a crafting conflict");
            var hand=online.getMainHandItem().copy();try{
                online.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
                var use=item.use(level,online,InteractionHand.MAIN_HAND);
                require(use.getResult().consumesAction()&&online.containerMenu!=online.inventoryMenu&&online.containerMenu.getClass().getName().toLowerCase().contains("chisel"),"Native restored chisel GUI failed to open");gui++;
            }finally{online.closeContainer();online.setItemInHand(InteractionHand.MAIN_HAND,hand);}
        }
        var quest=QuestDatabase.INSTANCE.getValue(92929);
        require(quest.getInfo().getProp(funwayguy.betterquesting.properties.CommonProps.LOGIC_TASK)==funwayguy.betterquesting.enums.ELogic.OR,"Chisel alternatives became mandatory together");
        var task=(TaskRetrieval)quest.getTasks().getValue(769);var held=new ItemStack(LegacyChisels.OBSIDIAN.get());held.setDamageValue(127);fake.getInventory().add(held);
        task.onInventoryChange(participant,769,92929,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(fake.getInventory()));task.detect(participant,769,92929);
        require(participant.PLAYER.progress.getTaskData(92929,769).getBoolean("isComplete")&&fake.getInventory().countItem(LegacyChisels.OBSIDIAN.get())==1,"Damaged obsidian chisel fails quest or is consumed");
        report.addProperty("legacy_chisel_restored_tiers_native_carving_and_recipe_checks",2);
        report.addProperty("legacy_chisel_native_mode_roundtrips",modes);
        report.addProperty("legacy_chisel_native_menus_opened_for_connected_client",gui);
        report.addProperty("legacy_chisel_original_iron_diamond_settings_and_or_quest_checked",true);
    }
}
