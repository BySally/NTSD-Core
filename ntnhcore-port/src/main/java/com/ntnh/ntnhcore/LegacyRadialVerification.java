package com.ntnh.ntnhcore;

import com.google.gson.*;
import com.hbm.api.energymk2.PowerNetwork;
import com.hbm.api.fluidmk2.FluidNetwork;
import com.hbm.inventory.fluid.*;
import com.hbm.items.machine.IItemFluidId;
import com.hbm.lib.ForgeDirection;
import com.hbm.tileentity.machine.storage.TileEntityBarrel;
import funwayguy.betterquesting.questing.player.ParticipantInfo;
import funwayguy.betterquesting.questing.quests.QuestDatabase;
import funwayguy.bq_standard.questing.tasks.TaskRetrieval;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import java.util.*;

/** Source behavior plus real HBM graph, native filled containers, menu and quest integration. */
final class LegacyRadialVerification {
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static ItemStack item(String id){return new ItemStack(BuiltInRegistries.ITEM.getOptional(new ResourceLocation(id)).orElseThrow());}
    static void pistons(LegacyRadialEngine.Engine e,int count){for(int i=0;i<9;i++)e.setItem(i,i<count?item("hbm:piston_selenium"):ItemStack.EMPTY);}
    static LegacyRadialEngine.Engine place(ServerLevel l,BlockPos p){l.setBlock(p,LegacyRadialEngine.BLOCK.get().defaultBlockState(),3);return (LegacyRadialEngine.Engine)l.getBlockEntity(p);}
    static void clear(ServerLevel l,Set<BlockPos> ps){for(var p:ps)l.setBlock(p,Blocks.AIR.defaultBlockState(),3);}
    static void run(MinecraftServer server,JsonObject report)throws Exception {
        var l=server.overworld();var center=new BlockPos(448,300,448);var area=new HashSet<BlockPos>();
        for(var d:Direction.values())for(int i=0;i<=4;i++)area.add(center.relative(d,i));
        for(var p:area)require(l.isEmptyBlock(p),"Radial fixture area occupied: "+p);
        var initialItems=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(6)).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
        var actor=server.getPlayerList().getPlayerByName("NTNHNovTest");require(actor!=null,"Connected isolated client required for radial menu");
        try {
            var e=place(l,center);long base=LegacyRadialEngine.energyPerMb(Fluids.DIESEL);require(base>0,"Native diesel has no combustion energy");
            e.tank.setFill(1000);pistons(e,2);e.tick();require(e.power==0&&e.tank.getFill()==1000,"Two pistons run the radial engine");
            for(int count=3;count<=9;count++){
                pistons(e,count);e.setPower(0);e.tank.setFill(1000);e.tick();
                require(e.power==Math.min(base*count,e.getMaxPower())&&e.tank.getFill()==1000-count,"Radial power/fuel diverges at "+count+" pistons");
            }
            pistons(e,9);e.tank.setFill(100);e.setPower(e.getMaxPower());e.tick();require(e.power==e.getMaxPower()&&e.tank.getFill()==91,"Full-buffer behavior differs from source");
            e.tank.setFill(1);e.setPower(0);e.tick();require(e.power==Math.min(base*9,e.getMaxPower())&&e.tank.getFill()==0,"Final partial fuel tick differs from source");
            for(var fuel:List.of(Fluids.WATER,Fluids.KEROSENE,Fluids.GAS)){
                e.tank.setTankType(fuel);e.tank.setFill(1000);e.setPower(0);e.tick();require(e.power==0&&e.tank.getFill()==1000,"Unsupported fuel burns: "+fuel);
            }
            e.tank.setTankType(Fluids.NITAN);require(e.getMaxPower()==2500000,"NITAN buffer multiplier lost");
            e.tank.setTankType(Fluids.DIESEL);e.tank.setFill(12345);e.setPower(123456);e.setItem(13,item("hbm:battery_redstone"));
            var saved=e.saveWithoutMetadata();var restored=new LegacyRadialEngine.Engine(center,e.getBlockState());restored.load(saved);
            require(restored.power==123456&&restored.pistonCount==9&&restored.tank.getFill()==12345&&restored.tank.getFluid()==Fluids.DIESEL&&!restored.getItem(13).isEmpty(),"Radial chunk NBT loses inventory/tank/energy");
            var wire=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{e.serialize(wire);restored.deserialize(wire);require(restored.power==e.power&&restored.pistonCount==9&&restored.tank.getFill()==12345,"Radial tracking packet loses state");}finally{wire.release();}
            e.setItem(13,ItemStack.EMPTY);e.setPower(0);pistons(e,0);e.tank.setFill(0);
            var request=new JsonObject();request.addProperty("item","hbm:canister_full");request.addProperty("fluid","DIESEL");request.addProperty("amount",1000);
            var full=HbmQuestRequirement.display(request);require(HbmQuestRequirement.matches(request,full),"Native diesel fixture is empty");
            e.setItem(9,full);e.tick();require(e.tank.getFill()==1000&&e.getItem(9).isEmpty()&&!e.getItem(10).isEmpty(),"Native canister fails to drain or empty container lost");
            e.setItem(9,full.copy());e.setItem(10,item("minecraft:dirt"));e.tick();require(e.tank.getFill()==1000&&!e.getItem(9).isEmpty(),"Blocked empty-container slot consumes fuel canister");
            e.setItem(9,ItemStack.EMPTY);e.setItem(10,ItemStack.EMPTY);
            var top=e.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(IllegalStateException::new);require(top.insertItem(0,item("minecraft:dirt"),false).is(Items.DIRT),"Hopper inserts invalid radial fuel");
            require(top.insertItem(0,full.copy(),false).isEmpty(),"Top hopper cannot insert fuel");e.tick();
            var bottom=e.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.DOWN).orElseThrow(IllegalStateException::new);require(!bottom.extractItem(0,1,false).isEmpty(),"Bottom hopper cannot extract empty canister");
            require(e.inventory.insertItem(0,new ItemStack(item("hbm:piston_selenium").getItem(),9),false).getCount()==8,"Piston slot allows more than one piston");
            e.setItem(0,ItemStack.EMPTY);e.setPower(5000);e.setItem(13,item("hbm:battery_redstone"));e.tick();var battery=(com.hbm.api.energymk2.IBatteryItem)e.getItem(13).getItem();
            require(battery.getCharge(e.getItem(13))>0&&e.power+battery.getCharge(e.getItem(13))==5000,"Radial battery charge loses or creates HE");
            e.clearContent();e.setPower(50000);e.tank.setFill(0);
            // Real HBM cable -> restored converter; only the bottom power face is permitted.
            var cable=center.below();var target=center.below(2);l.setBlock(cable,BuiltInRegistries.BLOCK.get(new ResourceLocation("hbm:red_cable")).defaultBlockState(),3);l.setBlock(target,LegacyEnergyConverter.BLOCK.get().defaultBlockState(),3);
            var converter=(LegacyEnergyConverter.Converter)l.getBlockEntity(target);
            for(int i=0;i<12&&converter.getPower()==0;i++){e.tick();PowerNetwork.distributeAll(l);}
            require(converter.getPower()>0&&converter.getPower()+e.power==50000,"Radial output fails native HBM cable/HE conservation");
            require(e.canConnect(ForgeDirection.DOWN)&&!e.canConnect(ForgeDirection.UP)&&!e.canConnect(ForgeDirection.EAST),"Radial HE exposed on wrong side");
            report.addProperty("radial_native_he_output_conserved",true);clear(l,area);
            // Real native HBM tank supplies through the native fluid network on all six faces.
            int faces=0;
            for(var side:Direction.values()){
                e=place(l,center);var tankPos=center.relative(side);l.setBlock(tankPos,BuiltInRegistries.BLOCK.get(new ResourceLocation("hbm:barrel_steel")).defaultBlockState(),3);
                var source=(TileEntityBarrel)l.getBlockEntity(tankPos);require(source!=null,"Native fluid tank missing");source.tank.setTankType(Fluids.DIESEL);source.tank.setFill(5000);source.mode=2;
                for(int i=0;i<12&&e.tank.getFill()==0;i++){TileEntityBarrel.serverTick(l,tankPos,source.getBlockState(),source);e.tick();FluidNetwork.distributeAll(l);}
                require(e.tank.getFill()>0&&e.tank.getFill()+source.tank.getFill()==5000,"Native diesel transfer fails/conservation differs on "+side);faces++;clear(l,area);
            }
            report.addProperty("radial_native_fluid_input_faces_checked",faces);
            e=place(l,center);var menu=new LegacyRadialEngine.Menu(1,actor.getInventory(),e);require(menu.slots.size()==50&&menu.data.getCount()==10&&menu.fuelCapacity()==16000,"Radial menu slot/data count wrong");
            var invSnapshot=actor.getInventory().save(new net.minecraft.nbt.ListTag());
            try{actor.getInventory().clearContent();actor.getInventory().setItem(9,new ItemStack(item("hbm:piston_selenium").getItem(),9));menu.quickMoveStack(actor,14);require(e.countPistons()==9&&actor.getInventory().getItem(9).isEmpty(),"Shift-click does not distribute pistons");
                e.power=250000;require(menu.power()==250000&&menu.capacity()==250000,"Menu truncates energy above 65535");
                net.minecraftforge.network.NetworkHooks.openScreen(actor,e,center);require(actor.containerMenu instanceof LegacyRadialEngine.Menu,"Real connected radial menu did not open");actor.closeContainer();
            }finally{actor.closeContainer();actor.getInventory().load(invSnapshot);actor.inventoryMenu.broadcastChanges();}
            e.clearContent();e.setItem(0,item("hbm:piston_selenium"));e.setItem(13,item("hbm:battery_redstone"));
            // Native fluid barrels may leave their own carried-fluid items when a fixture is removed.
            // Compare newly spawned UUIDs so those unrelated drops cannot change the engine break assertion.
            var before=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(2)).stream().map(net.minecraft.world.entity.Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            l.destroyBlock(center,true);var drops=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(2)).stream().filter(x->!before.contains(x.getUUID())).toList();
            require(drops.stream().mapToInt(x->x.getItem().getCount()).sum()==3&&drops.stream().anyMatch(x->x.getItem().is(LegacyRadialEngine.ITEM.get())),"Breaking radial engine duplicates or loses block/inventory");drops.forEach(net.minecraft.world.entity.Entity::discard);
            report.addProperty("radial_nbt_packet_menu_battery_container_automation_and_break_checked",true);
        }finally{actor.closeContainer();clear(l,area);l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(center).inflate(6)).stream().filter(x->!initialItems.contains(x.getUUID())).forEach(net.minecraft.world.entity.Entity::discard);}
        var recipe=(CraftingRecipe)server.getRecipeManager().byKey(new ResourceLocation("ntnhcore:legacy_hbm/machine_selenium")).orElseThrow();
        var matrix=new TransientCraftingContainer(new AbstractContainerMenu(null,0){@Override public boolean stillValid(Player p){return true;}@Override public ItemStack quickMoveStack(Player p,int s){return ItemStack.EMPTY;}},3,3);
        for(int i=0;i<9;i++)matrix.setItem(i,recipe.getIngredients().get(i).getItems()[0].copy());
        require(recipe.matches(matrix,l)&&server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,matrix,l).orElseThrow().assemble(matrix,server.registryAccess()).is(LegacyRadialEngine.ITEM.get()),"Radial original recipe loses conflict");matrix.setItem(0,item("minecraft:dirt"));require(!recipe.matches(matrix,l),"Dirt replaces dura pipe");
        var participant=new ParticipantInfo(actor.getUUID());var progress=(funwayguy.xbt.std.XBTTagCompound)participant.PLAYER.progress.writeToXBT(new funwayguy.xbt.std.XBTTagCompound()).copy();var inventory=actor.getInventory().save(new net.minecraft.nbt.ListTag());
        try{actor.getInventory().clearContent();participant.PLAYER.progress.clearTaskData(18177,257);var task=(TaskRetrieval)QuestDatabase.INSTANCE.getValue(18177).getTasks().getValue(257);task.detect(participant,257,18177);require(!participant.PLAYER.progress.getTaskData(18177,257).getBoolean("isComplete"),"Empty radial quest completes");
            actor.getInventory().add(new ItemStack(LegacyRadialEngine.ITEM.get()));task.onInventoryChange(participant,257,18177,new funwayguy.bq_standard.bootstrap.forge.events.InvoProxy(actor.getInventory()));task.detect(participant,257,18177);
            require(participant.PLAYER.progress.getTaskData(18177,257).getBoolean("isComplete")&&actor.getInventory().countItem(LegacyRadialEngine.ITEM.get())==1,"Radial quest fails automatic acquisition/consumes engine");
        }finally{participant.PLAYER.progress.clearAll();participant.PLAYER.progress.readFromXBT(progress);participant.markDirty(18177);actor.getInventory().clearContent();actor.getInventory().load(inventory);actor.inventoryMenu.broadcastChanges();}
        var definitions=LegacyBagDrops.definitionsForAudit();int states=0;
        for(var spec:Map.of("1_0/canister_lubricant","LUBRICANT","2_0/lv2_canister_full_111","KEROSENE_REFORM").entrySet()){
            var req=new JsonObject();req.addProperty("item","hbm:canister_full");req.addProperty("fluid",spec.getValue());req.addProperty("amount",1000);
            require(HbmQuestRequirement.matches(req,definitions.get(spec.getKey())),"HBM reward canister lost actual fluid "+spec);states++;
        }
        require(definitions.get("3_0/lv3_apple_schrabidium_1").is(item("hbm:apple_schrabidium_ingot").getItem()),"Wrong schrabidium apple tier");
        report.addProperty("radial_original_recipe_and_auto_nonconsuming_quest_checked",true);report.addProperty("hbm_reward_filled_canisters_checked",states);report.addProperty("radial_piston_counts_checked",7);
    }
}
