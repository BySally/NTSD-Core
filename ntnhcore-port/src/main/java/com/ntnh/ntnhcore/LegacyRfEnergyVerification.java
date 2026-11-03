package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.hbm.api.energymk2.PowerNetwork;
import com.hbm.interfaces.ILookOverlay;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.*;

/** Native FE contract, real HBM cables, state round trips and an actual lossy two-converter loop. */
final class LegacyRfEnergyVerification {
    private static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
    private static LegacyRfEnergyConverter.Converter place(ServerLevel level,BlockPos p){
        level.setBlock(p,LegacyRfEnergyConverter.BLOCK.get().defaultBlockState(),3);
        return (LegacyRfEnergyConverter.Converter)level.getBlockEntity(p);
    }
    private static void clear(ServerLevel level,Set<BlockPos> area){for(var p:area)level.setBlock(p,Blocks.AIR.defaultBlockState(),3);}
    static void run(MinecraftServer server,JsonObject report)throws Exception{
        require(LegacyRfEnergyConverter.RF_USED.get()==2&&LegacyRfEnergyConverter.HE_CREATED.get()==5&&LegacyRfEnergyConverter.INPUT_DECAY.get()==0D,"RF converter validation settings differ from original");
        var level=server.overworld();var pos=new BlockPos(424,300,424);var area=new HashSet<BlockPos>();area.add(pos);
        for(var d:Direction.values()){area.add(pos.relative(d));area.add(pos.relative(d,2));}
        for(var p:area)require(level.isEmptyBlock(p),"RF converter validation area occupied "+p);
        try{
            var tile=place(level,pos);
            require(tile.getReceiverSpeed()==0&&tile.getProviderSpeed()==5_000_000,"RF converter accepts HE or provider speed differs");
            require(tile.transferPower(999,false)==999&&tile.getPower()==0,"RF converter absorbs HE");
            for(var side:Direction.values()){
                var cap=tile.getCapability(ForgeCapabilities.ENERGY,side).orElseThrow(IllegalStateException::new);
                require(cap.canReceive()&&!cap.canExtract()&&cap.extractEnergy(1000,false)==0,"Wrong FE direction on "+side);
                require(cap.receiveEnergy(Integer.MAX_VALUE,true)==1_000_000&&cap.getEnergyStored()==0,"FE simulation mutates state/overflows");
                require(cap.receiveEnergy(-1,false)==0,"Negative FE received");
                require(PowerNetwork.handlerAtFace(level,pos,side)==tile,"Native HE face absent "+side);
            }
            tile.energy.receiveEnergy(1,false);tile.tick();require(tile.getPower()==2&&tile.energy.getEnergyStored()==0,"Original one-RF integer rounding changed");
            tile.setPower(0);tile.energy.receiveEnergy(2,false);tile.tick();require(tile.getPower()==5,"Original two-RF conversion changed");
            tile.setPower(0);tile.energy.receiveEnergy(1_000_000,false);tile.tick();require(tile.getPower()==2_500_000&&tile.energy.getEnergyStored()==0,"Full input ratio changed");
            tile.energy.receiveEnergy(1_000_000,false);tile.tick();require(tile.getPower()==5_000_000,"Two full inputs do not fill HE buffer");
            tile.energy.receiveEnergy(1_000_000,false);tile.tick();require(tile.getPower()==5_000_000&&tile.energy.getEnergyStored()==1_000_000,"Full HE buffer loses FE");
            tile.setPower(4_999_999);tile.tick();require(tile.getPower()==4_999_999&&tile.energy.getEnergyStored()==1_000_000,"One-HE output room consumes FE");
            tile.setPower(4_999_997);tile.tick();require(tile.getPower()==4_999_999&&tile.energy.getEnergyStored()==999_999,"Near-full original truncation changed");
            var saved=tile.saveWithoutMetadata();var copy=new LegacyRfEnergyConverter.Converter(pos,tile.getBlockState());copy.load(saved);
            require(copy.getPower()==tile.getPower()&&copy.energy.getEnergyStored()==tile.energy.getEnergyStored(),"RF converter NBT round trip loses energy");
            var buf=new FriendlyByteBuf(Unpooled.buffer());try{tile.serialize(buf);copy.deserialize(buf);require(copy.getPower()==tile.getPower()&&copy.energy.getEnergyStored()==tile.energy.getEnergyStored(),"RF converter packet loses energy");}finally{buf.release();}
            var invalid=new CompoundTag();invalid.putLong("power",Long.MAX_VALUE);invalid.putInt("Energy",Integer.MAX_VALUE);copy.load(invalid);
            require(copy.getPower()==5_000_000&&copy.energy.getEnergyStored()==1_000_000,"Invalid NBT overfills RF converter");
            var cap=tile.getCapability(ForgeCapabilities.ENERGY,Direction.UP);tile.invalidateCaps();require(!cap.isPresent(),"Removed RF capability remains valid");tile.reviveCaps();require(tile.getCapability(ForgeCapabilities.ENERGY,Direction.UP).isPresent(),"RF capability not revived");
            var overlay=((ILookOverlay)tile.getBlockState().getBlock()).getLookOverlay(level,pos,null);
            require(overlay!=null&&overlay.lines().size()==2&&overlay.lines().get(0).contains("999999"),"RF overlay lost buffer");
            var drops=Block.getDrops(tile.getBlockState(),level,pos,tile,null,new ItemStack(Items.IRON_PICKAXE));
            require(drops.size()==1&&drops.get(0).is(LegacyRfEnergyConverter.ITEM.get())&&!drops.get(0).hasTag(),"RF converter break duplicates stored energy");
            clear(level,area);
            for(var side:Direction.values()){
                tile=place(level,pos);var cable=pos.relative(side);var sinkPos=pos.relative(side,2);
                level.setBlock(cable,BuiltInRegistries.BLOCK.get(new ResourceLocation("hbm:red_cable")).defaultBlockState(),3);
                level.setBlock(sinkPos,LegacyEnergyConverter.BLOCK.get().defaultBlockState(),3);
                var sink=(LegacyEnergyConverter.Converter)level.getBlockEntity(sinkPos);
                var input=tile.getCapability(ForgeCapabilities.ENERGY,side.getOpposite()).orElseThrow(IllegalStateException::new);
                require(input.receiveEnergy(10000,false)==10000,"FE input face rejected "+side);
                for(int n=0;n<12&&sink.getPower()==0;n++){tile.tick();PowerNetwork.distributeAll(level);}
                require(sink.getPower()>0&&sink.getPower()+tile.getPower()==25_000,"Actual HBM cable output loses/creates HE on "+side);
                clear(level,area);
            }
            report.addProperty("rf_converter_native_cable_output_faces_checked",6);
            for(var side:Direction.values()){
                tile=place(level,pos);var sinkPos=pos.relative(side);level.setBlock(sinkPos,LegacyEnergyConverter.BLOCK.get().defaultBlockState(),3);
                var sink=(LegacyEnergyConverter.Converter)level.getBlockEntity(sinkPos);tile.energy.receiveEnergy(10000,false);tile.tick();
                require(sink.getPower()==25_000&&tile.getPower()==0,"Original direct touching HE output failed "+side);clear(level,area);
            }
            report.addProperty("rf_converter_direct_output_faces_checked",6);
            // Both blocks connected by their native interfaces, feeding the FE output back to the FE input.
            tile=place(level,pos);var sinkPos=pos.east();level.setBlock(sinkPos,LegacyEnergyConverter.BLOCK.get().defaultBlockState(),3);
            var forward=(LegacyEnergyConverter.Converter)level.getBlockEntity(sinkPos);tile.energy.receiveEnergy(1001,false);
            long before=5_005;int steps=32;
            for(int n=0;n<steps;n++){
                tile.tick();PowerNetwork.distributeAll(level);forward.tick();
                long total=tile.getPower()+forward.getPower()+5L*(tile.energy.getEnergyStored()+forward.energy.getEnergyStored());
                require(total<=before,"Closed converter loop creates energy at step "+n);before=total;
            }
            require(before<20,"Closed converter loop is not lossy");
            report.addProperty("rf_converter_closed_loop_steps_checked",steps);report.addProperty("rf_converter_closed_loop_remaining_he_equivalent",before);
        }finally{clear(level,area);}
        var recipe=(CraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("ntnhcore:legacy_hbm/machine_converter_rf_he")).orElseThrow();
        var grid=new TransientCraftingContainer(new AbstractContainerMenu(null,0){
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return true;}
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player,int slot){return ItemStack.EMPTY;}
        },3,3);
        for(int n=0;n<9;n++)grid.setItem(n,recipe.getIngredients().get(n).getItems()[0].copy());
        require(recipe.matches(grid,level)&&server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow().assemble(grid,server.registryAccess()).is(LegacyRfEnergyConverter.ITEM.get()),"RF original recipe loses crafting conflict");
        require(grid.getItem(3).is(BuiltInRegistries.ITEM.get(new ResourceLocation("hbm:wire_fine_red_copper"))),"Old Mingrade is not mapped to original red copper");
        grid.setItem(3,new ItemStack(Items.COPPER_INGOT));require(!recipe.matches(grid,level),"Ordinary copper bypasses fine Mingrade wire requirement");
        report.addProperty("rf_converter_original_recipe_buffers_rounding_nbt_packet_caps_drop_and_overlay_checked",true);
        report.addProperty("rf_converter_closed_loop_does_not_create_energy",true);
    }
}
