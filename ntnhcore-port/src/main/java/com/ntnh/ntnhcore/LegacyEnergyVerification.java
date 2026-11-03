package com.ntnh.ntnhcore;

import com.google.gson.JsonObject;
import com.hbm.api.energymk2.IEnergyHandlerMK2;
import com.hbm.api.energymk2.PowerNetwork;
import com.hbm.interfaces.ILookOverlay;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.*;

/** Native loaded-server integration, only reached with -Dntnhcore.validation=true. */
final class LegacyEnergyVerification {
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static BlockState state(String id){return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(id)).orElseThrow().defaultBlockState();}
    private static LegacyEnergyConverter.Converter place(ServerLevel level,BlockPos pos){
        level.setBlock(pos,LegacyEnergyConverter.BLOCK.get().defaultBlockState(),3);
        return (LegacyEnergyConverter.Converter)level.getBlockEntity(pos);
    }
    private static void clear(ServerLevel level,Collection<BlockPos> positions){for(var pos:positions)level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
    static void run(MinecraftServer server,JsonObject report)throws Exception {
        require(LegacyEnergyConverter.HE_USED.get()==5&&LegacyEnergyConverter.RF_CREATED.get()==1&&LegacyEnergyConverter.INPUT_DECAY.get()==0D,"Isolated validation must use the source NTNH energy settings");
        var level=server.overworld();var center=new BlockPos(352,300,352);var area=new HashSet<BlockPos>();
        for(Direction d:Direction.values()){area.add(center.relative(d));area.add(center.relative(d,2));}
        area.add(center);area.add(center.above().north());area.add(center.below().north());
        for(var p:area)require(level.isEmptyBlock(p),"Energy validation area is occupied: "+p);
        try {
            var tile=place(level,center);
            require(tile.getPriority()==IEnergyHandlerMK2.ConnectionPriority.LOW&&tile.getProviderSpeed()==0,"Converter can feed HE back into the graph or has the wrong priority");
            for(Direction side:Direction.values()){
                var cap=tile.getCapability(ForgeCapabilities.ENERGY,side).orElseThrow(IllegalStateException::new);
                require(cap.canExtract()&&!cap.canReceive()&&cap.receiveEnergy(9000,false)==0,"Converter accepts FE on "+side);
                require(PowerNetwork.handlerAtFace(level,center,side)==tile,"HBM does not recognize HE input face "+side);
            }
            require(tile.transferPower(Long.MAX_VALUE,true)==Long.MAX_VALUE-5_000_000&&tile.getPower()==0,"HE simulation mutates state or overflows");
            tile.setPower(9);tile.tick();require(tile.getPower()==4&&tile.energy.getEnergyStored()==1,"HE remainder was lost");
            require(tile.energy.extractEnergy(Integer.MAX_VALUE,true)==1&&tile.energy.getEnergyStored()==1,"FE simulation mutates state");
            require(tile.energy.extractEnergy(-1,false)==0&&tile.energy.extractEnergy(1,false)==1,"FE extraction contract failed");
            tile.setPower(5_000_000);tile.tick();require(tile.getPower()==0&&tile.energy.getEnergyStored()==1_000_000,"Full converter violates the source 5:1 ratio");
            tile.setPower(5_000_000);tile.tick();require(tile.getPower()==5_000_000&&tile.energy.getEnergyStored()==1_000_000,"Full FE buffer loses HE");
            var saved=tile.saveWithoutMetadata();var restored=new LegacyEnergyConverter.Converter(center,tile.getBlockState());restored.load(saved);
            require(restored.getPower()==5_000_000&&restored.energy.getEnergyStored()==1_000_000,"Chunk NBT loses energy buffers");
            var corrupt=new CompoundTag();corrupt.putLong("power",Long.MAX_VALUE);corrupt.putInt("Energy",Integer.MAX_VALUE);restored.load(corrupt);
            require(restored.getPower()==5_000_000&&restored.energy.getEnergyStored()==1_000_000,"Invalid NBT exceeds buffer capacity");
            var cap=tile.getCapability(ForgeCapabilities.ENERGY,Direction.UP);tile.invalidateCaps();require(!cap.isPresent(),"Removed capability stays usable");tile.reviveCaps();
            require(tile.getCapability(ForgeCapabilities.ENERGY,Direction.UP).isPresent(),"Revived capability is missing");
            var drops=Block.getDrops(tile.getBlockState(),level,center,tile,null,new ItemStack(Items.IRON_PICKAXE));
            require(drops.size()==1&&drops.get(0).is(LegacyEnergyConverter.ITEM.get())&&!drops.get(0).hasTag(),"Breaking duplicates stored energy in a block item");
            var overlay=((ILookOverlay)tile.getBlockState().getBlock()).getLookOverlay(level,center,null);
            require(overlay!=null&&overlay.lines().size()==2&&overlay.lines().get(0).contains("5000000"),"Native HBM overlay loses stored energy");
            clear(level,area);
            // Exercise the real HBM source, cable cells and distribution on every input face.
            Class<?> genClass=Class.forName("com.hbm.tileentity.machine.TileEntityCreativeGenerator");
            var genTick=genClass.getMethod("serverTick",Level.class,BlockPos.class,BlockState.class,genClass);
            for(Direction side:Direction.values()){
                tile=place(level,center);var cable=center.relative(side);var source=center.relative(side,2);
                level.setBlock(cable,state("hbm:red_cable"),3);level.setBlock(source,state("hbm:creative_generator"),3);
                var generator=level.getBlockEntity(source);require(genClass.isInstance(generator),"Native HE source is missing");
                for(int pass=0;pass<12&&tile.getPower()==0;pass++){genTick.invoke(null,level,source,level.getBlockState(source),generator);PowerNetwork.distributeAll(level);}
                require(tile.getPower()>0&&tile.getPower()<=5_000_000,"Native HBM cable does not feed converter on "+side);
                clear(level,area);
            }
            report.addProperty("converter_native_hbm_cable_input_faces",6);
            // Real AE2 grid storage behind its energy acceptor, not a mock FE receiver.
            var received=new JsonObject();
            for(Direction side:Direction.values()){
                tile=place(level,center);var acceptorPos=center.relative(side);
                var cellPos=(side.getAxis()==Direction.Axis.Y?acceptorPos.north():acceptorPos.above());area.add(cellPos);
                require(level.isEmptyBlock(cellPos),"AE2 validation area occupied");
                level.setBlock(acceptorPos,state("ae2:energy_acceptor"),3);level.setBlock(cellPos,state("ae2:energy_cell"),3);
                var acceptor=level.getBlockEntity(acceptorPos);var cell=level.getBlockEntity(cellPos);
                acceptor.getClass().getMethod("onReady").invoke(acceptor);cell.getClass().getMethod("onReady").invoke(cell);
                var input=acceptor.getCapability(ForgeCapabilities.ENERGY,side.getOpposite()).orElseThrow(IllegalStateException::new);
                int demand=input.receiveEnergy(200_000,true);require(demand>0,"AE2 grid refuses simulated FE on "+side);
                tile.setPower(1_000_000);tile.tick();
                double stored=((Number)cell.getClass().getMethod("getAECurrentPower").invoke(cell)).doubleValue();
                int spent=200_000-tile.energy.getEnergyStored();
                require(spent>0&&spent<=demand&&stored>0,"Converter does not power real AE2 storage on "+side);
                require(tile.getPower()==0,"HE cost differs from output conversion on "+side);
                received.addProperty(side.getName(),stored);clear(level,area);
            }
            report.add("converter_ae2_cell_energy_received_each_output_face",received);
        }finally{clear(level,area);}
        var recipe=(CraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("ntnhcore:legacy_hbm/machine_converter_he_rf")).orElseThrow();
        var grid=new TransientCraftingContainer(new AbstractContainerMenu(null,0){
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return true;}
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p,int slot){return ItemStack.EMPTY;}
        },3,3);
        for(int i=0;i<9;i++)grid.setItem(i,recipe.getIngredients().get(i).getItems()[0].copy());
        require(recipe.matches(grid,level)&&server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow().assemble(grid,server.registryAccess()).is(LegacyEnergyConverter.ITEM.get()),"Old converter recipe loses a crafting conflict");
        grid.setItem(0,new ItemStack(Items.DIRT));require(!recipe.matches(grid,level),"Dirt bypasses converter recipe");
        var player=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"EnergyQuest"));
        ProfileManager.get().makeOrUpdate(player.getUUID(),"EnergyQuest");var participant=new ParticipantInfo(player.getUUID());
        var task=(TaskRetrieval)QuestDatabase.INSTANCE.getValue(12545).getTasks().getValue(257);task.detect(participant,257,12545);
        require(!participant.PLAYER.progress.getTaskData(12545,257).getBoolean("isComplete"),"Empty inventory completes converter quest");
        player.getInventory().add(new ItemStack(LegacyEnergyConverter.ITEM.get()));
        task.onInventoryChange(participant,257,12545,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(player.getInventory()));task.detect(participant,257,12545);
        require(participant.PLAYER.progress.getTaskData(12545,257).getBoolean("isComplete")&&player.getInventory().countItem(LegacyEnergyConverter.ITEM.get())==1,"Converter quest fails inventory detection or consumes the block");
        report.addProperty("converter_original_recipe_and_nonconsuming_quest_checked",true);
        report.addProperty("converter_buffers_remainder_simulation_nbt_and_capability_lifecycle_checked",true);
    }
}
