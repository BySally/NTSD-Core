package com.ntnh.ntnhcore;

import appeng.api.config.*;
import appeng.api.networking.GridFlags;
import appeng.api.util.AEColor;
import appeng.blockentity.grid.AENetworkBlockEntity;
import com.glodblock.github.extendedae.common.me.wireless.WirelessConnect;
import com.glodblock.github.extendedae.common.tileentities.*;
import com.glodblock.github.extendedae.util.WirelessHelpers;
import com.google.gson.JsonObject;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.player.profile.ProfileManager;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import java.util.*;

/** Real native transport, interaction, recipe and BQ checks in the isolated world only. */
final class LegacyWirelessVerification {
    private static final String NS="expatternprovider:";
    private static void require(boolean v,String m){if(!v)throw new IllegalStateException(m);}
    private static Item item(String id){return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(id)).orElseThrow();}
    private static Block block(String id){return BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(id)).orElseThrow();}
    private static AENetworkBlockEntity place(ServerLevel level,BlockPos pos,String id){
        level.setBlock(pos,block(id).defaultBlockState(),3);var tile=(AENetworkBlockEntity)level.getBlockEntity(pos);tile.onReady();return tile;
    }
    private static WirelessConnect[] links(AENetworkBlockEntity tile)throws Exception{
        var f=tile.getClass().getDeclaredField("connect");f.setAccessible(true);var value=f.get(tile);
        return value instanceof WirelessConnect c?new WirelessConnect[]{c}:(WirelessConnect[])value;
    }
    private static void pump(AENetworkBlockEntity... tiles)throws Exception{
        for(int pass=0;pass<4;pass++)for(var tile:tiles){
            if(tile instanceof TileWirelessConnector c)c.serverTick();else ((TileWirelessHub)tile).serverTick();
            for(var link:links(tile))link.updateStatus();
            if(tile instanceof TileWirelessConnector c)c.updatePowerUsage();else ((TileWirelessHub)tile).updatePowerUsage();
        }
    }
    private static InteractionResult click(ServerLevel level,BlockPos pos,ServerPlayer actor){
        var state=level.getBlockState(pos);return state.getBlock().use(state,level,pos,actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
    }
    private static void bind(ServerLevel level,BlockPos a,BlockPos b,ServerPlayer actor){
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item(NS+"wireless_tool")));
        require(click(level,a,actor).consumesAction(),"Native setup tool cannot select first device");
        require(click(level,b,actor).consumesAction(),"Native setup tool cannot link second device");
        require(!actor.getMainHandItem().hasTag(),"Successful native binding did not clear tool selection");
    }
    private static void clear(ServerLevel level,Collection<BlockPos> positions){for(var pos:positions)level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
    static void run(MinecraftServer server,JsonObject report)throws Exception{
        require(LegacyWireless.BASE.get()==1D&&LegacyWireless.DISTANCE_MULTIPLIER.get()==.1D&&LegacyWireless.MAX_RANGE.get()==0D,"Wireless validation must use source NTNH power settings");
        var actor=server.getPlayerList().getPlayerByName("NTNHNovTest");require(actor!=null,"Isolated connected client is required");
        var level=server.overworld();var a=new BlockPos(448,300,448);var b=a.east(1024);var c=a.north(8);var h=a.north(16);
        require(server.getLootData().getLootTable(new ResourceLocation(NS+"blocks/ex_emc_interface")).getRandomItems(
            new net.minecraft.world.level.storage.loot.LootParams.Builder(level).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY)).isEmpty(),"Absent ProjectE block does not have a valid empty table");
        report.addProperty("legacy_wireless_absent_projecte_loot_table_checked",true);
        var area=new LinkedHashSet<BlockPos>(List.of(a,b,c,h,a.above()));
        for(int i=0;i<9;i++)area.add(a.east(8+i*4).north(16));
        for(var p:area)require(level.isEmptyBlock(p),"Wireless validation area occupied: "+p);
        var hand=actor.getMainHandItem().copy();
        try{
            var first=(TileWirelessConnector)place(level,a,NS+"wireless_connect");var second=(TileWirelessConnector)place(level,b,NS+"wireless_connect");
            var third=(TileWirelessConnector)place(level,c,NS+"wireless_connect");pump(first,second,third);
            require(!first.isConnected()&&first.getPowerUse()==0D,"Unlinked connector has a phantom link or drains power");
            level.getChunkAt(a).setUnsaved(false);level.getChunkAt(b).setUnsaved(false);
            bind(level,a,b,actor);pump(first,second);
            require(first.isConnected()&&second.isConnected()&&first.getOtherSide().equals(b)&&second.getOtherSide().equals(a),"Native pair was not established above the modern 1000-block default limit");
            require(level.getChunkAt(a).isUnsaved()&&level.getChunkAt(b).isUnsaved(),"Relinking does not mark both chunks dirty");
            var n1=first.getGridNode();var n2=second.getGridNode();
            require(n1!=null&&n2!=null&&n1.getGrid()==n2.getGrid(),"Wireless endpoints do not share a real AE2 grid");
            require(n1.hasFlag(GridFlags.DENSE_CAPACITY)&&n1.getMaxChannels()==32,"Wireless endpoint is not dense-capacity");
            double expected=1D+.1D*1024D*Math.log(1024D*1024D+3D);
            require(Math.abs(first.getPowerUse()-expected)<1e-7&&Math.abs(second.getPowerUse()-expected)<1e-7,"Pair does not use the old power law at both ends");
            // A real energy cell on one side, accessed through the other side's native grid service.
            var cell=place(level,a.above(),"ae2:energy_cell");
            var energy=n1.getGrid().getEnergyService();double overflow=energy.injectPower(50_000D,Actionable.MODULATE);
            double before=((Number)cell.getClass().getMethod("getAECurrentPower").invoke(cell)).doubleValue();
            double received=n2.getGrid().getEnergyService().extractAEPower(1000D,Actionable.MODULATE,PowerMultiplier.ONE);
            double after=((Number)cell.getClass().getMethod("getAECurrentPower").invoke(cell)).doubleValue();
            require(overflow==0D&&received==1000D&&Math.abs(before-after-1000D)<1e-7,"Remote native AE2 grid cannot draw from the actual energy cell");
            first.getUpgrades().setItemDirect(0,new ItemStack(item("ae2:energy_card"),4));first.updatePowerUsage();
            require(Math.abs(first.getPowerUse()-expected)<1e-7,"Modern energy-card discount bypasses legacy power cost");
            first.recolourBlock(Direction.UP,AEColor.RED,actor);
            var saved=first.saveWithFullMetadata();var restored=(TileWirelessConnector)BlockEntity.loadStatic(a,first.getBlockState(),saved);
            require(restored!=null&&restored.getFrequency()==first.getFrequency()&&restored.getColor()==AEColor.RED&&restored.getUpgrades().getStackInSlot(0).getCount()==4,"Native frequency/color/upgrades fail NBT roundtrip");
            restored.disconnect(); // Detached verification instance must not retain an event listener.
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item(NS+"wireless_tool")));
            require(click(level,c,actor).consumesAction()&&click(level,b,actor)==InteractionResult.FAIL,"Occupied pair was silently rebound");
            pump(first,second,third);require(first.getGridNode().getGrid()==second.getGridNode().getGrid()&&!third.isConnected(),"Rejected binding changed the existing native pair");
            var cross=new CompoundTag();WirelessHelpers.bindWireless(cross,third.getNewFreq(),GlobalPos.of(Level.NETHER,a));
            require(WirelessHelpers.connectWireless(third::setFrequency,cross,level,c,actor,()->{})==InteractionResult.FAIL,"Cross-dimensional wireless link accepted");
            var self=new CompoundTag();WirelessHelpers.bindWireless(self,third.getNewFreq(),GlobalPos.of(Level.OVERWORLD,c));
            require(WirelessHelpers.connectWireless(third::setFrequency,self,level,c,actor,()->{})==InteractionResult.FAIL,"Self-link accepted");
            level.setBlock(b,Blocks.AIR.defaultBlockState(),3);pump(first);
            require(!first.isConnected()&&first.getPowerUse()==0D,"Broken endpoint leaves a live or power-consuming link");
            report.addProperty("legacy_wireless_pair_distance_blocks",1024);report.addProperty("legacy_wireless_pair_ae_per_tick_each_end",expected);
            report.addProperty("legacy_wireless_real_remote_cell_energy_extracted",received);
            report.addProperty("legacy_wireless_nbt_dirty_chunk_exclusivity_and_dimension_checks",true);
            clear(level,area);
            var hub=(TileWirelessHub)place(level,h,NS+"wireless_hub");pump(hub);double total=0D;
            var endpoints=new ArrayList<AENetworkBlockEntity>();endpoints.add(hub);
            for(int i=0;i<8;i++){
                var pos=a.east(8+i*4).north(16);var endpoint=(TileWirelessConnector)place(level,pos,NS+"wireless_connect");pump(endpoint);
                bind(level,i%2==0?h:pos,i%2==0?pos:h,actor);endpoints.add(endpoint);pump(endpoints.toArray(AENetworkBlockEntity[]::new));
                require(endpoint.isConnected()&&endpoint.getGridNode().getGrid()==hub.getMainNode().getNode().getGrid(),"Hub port "+i+" does not transport a native grid");
                total+=LegacyWireless.power(Math.sqrt(h.distSqr(pos)));
            }
            require(hub.allocatePort()==-1&&Math.abs(hub.getPowerUse()-total)<1e-7,"Eight-port hub accounting failed");
            var extra=a.east(8+8*4).north(16);var ninth=(TileWirelessConnector)place(level,extra,NS+"wireless_connect");pump(ninth);
            actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item(NS+"wireless_tool")));
            require(click(level,h,actor)==InteractionResult.FAIL,"Full hub accepted a ninth connection");
            hub.killPort(3);pump(endpoints.toArray(AENetworkBlockEntity[]::new));
            require(hub.allocatePort()==3&&Math.abs(hub.getPowerUse()-(total-LegacyWireless.power(20D)))<1e-7,"Disconnecting one hub port damages accounting or retains its drain");
            report.addProperty("legacy_wireless_native_hub_links_tested",8);report.addProperty("legacy_wireless_hub_capacity_reject_and_disconnect_checked",true);
        }finally{actor.setItemInHand(InteractionHand.MAIN_HAND,hand);clear(level,area);}
        recipes(server,report);quest(server,report);
    }
    private static void recipes(MinecraftServer server,JsonObject report){
        var level=server.overworld();int count=0;
        for(String name:new String[]{"wireless_connector","wireless_hub","wireless_tool","clear_wireless_tool","clear_advanced_wireless_tool"}){
            var recipe=(CraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("ntnhcore:legacy_ae2stuff/"+name)).orElseThrow();
            var grid=new TransientCraftingContainer(new AbstractContainerMenu(null,0){public boolean stillValid(Player p){return true;}public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}},3,3);
            var ing=recipe.getIngredients();for(int i=0;i<ing.size();i++){
                var choices=ing.get(i).getItems();require(ing.get(i).isEmpty()||choices.length>0,"Unresolved wireless recipe ingredient");
                if(choices.length>0){var stack=choices[0].copy();if(name.startsWith("clear_"))stack.getOrCreateTag().putLong("freq",1234L);grid.setItem(i,stack);}
            }
            require(recipe.matches(grid,level),"Legacy wireless recipe cannot be crafted: "+name);
            var result=server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,level).orElseThrow().assemble(grid,server.registryAccess());
            require(result.is(recipe.getResultItem(server.registryAccess()).getItem())&&result.getCount()==1&&!result.hasTag(),"Wireless crafting conflict, wrong count, or reset retains binding: "+name);count++;
        }
        require(server.getRecipeManager().byKey(new ResourceLocation(NS+"wireless_advanced_tool")).isPresent(),"Native advanced-tool recipe missing");
        report.addProperty("legacy_wireless_original_or_reset_recipes_checked",count);
    }
    private static void quest(MinecraftServer server,JsonObject report){
        var player=new ServerPlayer(server,server.overworld(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WirelessQuest"));
        ProfileManager.get().makeOrUpdate(player.getUUID(),"WirelessQuest");var p=new ParticipantInfo(player.getUUID());
        var task=(TaskRetrieval)QuestDatabase.INSTANCE.getValue(91137).getTasks().getValue(257);task.detect(p,257,91137);
        require(!p.PLAYER.progress.getTaskData(91137,257).getBoolean("isComplete"),"Empty inventory completes wireless task");
        player.getInventory().add(new ItemStack(item(NS+"wireless_tool")));task.detect(p,257,91137);
        require(!p.PLAYER.progress.getTaskData(91137,257).getBoolean("isComplete"),"Tool alone completes the two-item wireless task");
        player.getInventory().add(new ItemStack(item(NS+"wireless_connect")));
        task.onInventoryChange(p,257,91137,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(player.getInventory()));task.detect(p,257,91137);
        require(p.PLAYER.progress.getTaskData(91137,257).getBoolean("isComplete")&&player.getInventory().countItem(item(NS+"wireless_tool"))==1&&player.getInventory().countItem(item(NS+"wireless_connect"))==1,"Wireless inventory task fails or consumes either required item");
        report.addProperty("legacy_wireless_two_item_quest_nonconsuming_checked",true);
    }
}
